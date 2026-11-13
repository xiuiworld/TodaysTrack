package dev.todaystrack.server

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit

class GenerationWorker(
    private val root: File,
    private val store: JobStore,
    private val validateAudio: (File, File) -> Long = { file, run -> AudioValidator().validate(file,run) }
) {
    private val assets=File(root,"assets").apply { mkdirs() }
    private val evidence=File(root,"generation").apply { mkdirs() }
    fun mode(): String {
        val control=File(root,"dispatch.json")
        if(!control.exists()) return "FIXTURE"
        val config=runCatching { json.parseToJsonElement(control.readText()).jsonObject }.getOrNull() ?: return "FIXTURE"
        return if(config["enabled"]?.jsonPrimitive?.booleanOrNull==true && config["budgetUsd"]?.jsonPrimitive?.doubleOrNull==5.0 && !File(root,"billing-stop").exists()) "LIVE" else "FIXTURE"
    }
    suspend fun run() {
        while(currentCoroutineContext().isActive) {
            if(!processNext()) delay(1000)
        }
    }

    /** One bounded local work unit. Saved-result recovery never enters provider dispatch. */
    fun processNext(): Boolean {
        store.validationPending()?.let { job ->
            val id=job.getValue("id")!!
            val run=File(evidence,id).apply { mkdirs() }
            try {
                val final=File(assets,"$id.mp3")
                val partial=File(assets,"$id.mp3.part")
                val savedResponse=File(run,"response.json")
                val candidate=when {
                    final.isFile -> final
                    partial.isFile -> partial
                    savedResponse.isFile -> {
                        require(savedResponse.length()<=32L*1024*1024)
                        extractSavedResponse(savedResponse,partial,run)
                        partial
                    }
                    else -> error("SAVED_RESULT_MISSING")
                }
                finishAsset(job,candidate,run)
            } catch(e: Exception) {
                if(store.state(id)!="READY") store.updateState(id,"RECONCILING")
                recordFailure(run,e,"LOCAL_RESULT_RECOVERY_HELD")
            }
            return true
        }
        val job=store.queued() ?: return false
        val id=job.getValue("id")!!
        val mode=job.getValue("mode")!!
        if(mode=="LIVE" && mode()!="LIVE") return false
        val run=File(evidence,id).apply { mkdirs() }
        try {
            val payload=json.parseToJsonElement(job.getValue("payload")!!).jsonObject
            val body=compositionRequest(payload)
            if(!store.beginDispatch(id,body)) return true
            File(run,"input-snapshot.json").writeText(job.getValue("payload")!!)
            File(run,"request.json").writeText(body)
                val temp=File(assets,"$id.mp3.part")
                if(mode=="FIXTURE") {
                    val fixture=File(System.getenv("TODAYSTRACK_FIXTURE_AUDIO") ?: error("Fixture path required"))
                    require(fixture.isFile)
                    fixture.copyTo(temp,overwrite=true)
                    File(run,"provenance.json").writeText("{\"mode\":\"FIXTURE\",\"newAiGeneration\":false}")
                } else {
                    val key=System.getenv("GEMINI_API_KEY") ?: error("Server protected key unavailable")
                    val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
                    val request=HttpRequest.newBuilder(URI("https://generativelanguage.googleapis.com/v1beta/interactions"))
                        .timeout(Duration.ofSeconds(180)).header("Content-Type","application/json")
                        .header("Api-Revision","2026-05-20").header("x-goog-api-key",key).POST(HttpRequest.BodyPublishers.ofString(body)).build()
                    // No retry handler: SUBMITTING was committed before send, uncertain responses stay held.
                    val response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
                    if(response.statusCode() !in 200..299) {
                        response.body().close()
                        if(response.statusCode()==402 || response.statusCode()==429) File(root,"billing-stop").writeText("Provider billing/quota rejection: ${response.statusCode()}; no automatic retry")
                        store.updateState(id,if(response.statusCode() in 400..499) "FAILED_PROVIDER_REJECTION" else "RECONCILING")
                        return true
                    }
                    val responseFile=File(run,"response.json.part")
                    response.body().use { input -> responseFile.outputStream().use { out ->
                        val buffer=ByteArray(8192);var total=0L
                        while(true) { val n=input.read(buffer);if(n<0)break;total+=n;check(total<=32L*1024*1024);out.write(buffer,0,n) }
                    } }
                    val saved=File(run,"response.json")
                    Files.move(responseFile.toPath(),saved.toPath(),StandardCopyOption.ATOMIC_MOVE)
                    extractSavedResponse(saved,temp,run)
                }
                store.updateState(id,"VALIDATING")
                finishAsset(job,temp,run)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
                val state=store.state(id)
                if(state!="READY") store.updateState(id,if(state=="VALIDATING") "FAILED_VALIDATION" else "RECONCILING")
                recordFailure(run,e,"GENERATION_HELD")
        }
        return true
    }

    private fun extractSavedResponse(saved: File, temp: File, run: File) {
        val result=json.parseToJsonElement(saved.readText()).jsonObject
        val content=result["steps"]?.jsonArray.orEmpty().filter { it.jsonObject["type"]?.jsonPrimitive?.content=="model_output" }
            .flatMap { it.jsonObject["content"]?.jsonArray.orEmpty() }
        val audios=content.filter { it.jsonObject["type"]?.jsonPrimitive?.content=="audio" }
        require(audios.size==1) { "One audio result required" }
        val audio=audios.single().jsonObject
        require(audio.string("mime_type") in setOf("audio/mpeg","audio/mp3"))
        temp.writeBytes(Base64.getDecoder().decode(audio.string("data")))
        File(run,"returned-text.txt").writeText(content.filter { it.jsonObject["type"]?.jsonPrimitive?.content=="text" }
            .joinToString("\n") { it.jsonObject.string("text") })
        File(run,"provenance.json").writeText("{\"mode\":\"LIVE\",\"newAiGeneration\":true,\"model\":\"lyria-3.5\",\"listeningAssessed\":false}")
    }

    private fun finishAsset(job: Map<String,String?>, candidate: File, run: File) {
        val id=job.getValue("id")!!
        val duration=validateAudio(candidate,run)
        require(duration>0)
        val hash=sha256(candidate.readBytes())
        val final=File(assets,"$id.mp3")
        if(candidate!=final) Files.move(candidate.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        store.ready(id,hash,final.length(),duration)
        // READY is authoritative; diagnostic artifact failure cannot revoke a saved song.
        runCatching {
            File(run,"trace.json").writeText(buildJsonObject {
                put("jobId",id);put("inputHash",job.getValue("hash"))
                File(run,"request.json").takeIf { it.isFile }?.let { put("requestHash",sha256(it.readBytes())) }
                put("audioHash",hash);put("mode",job.getValue("mode"));put("durationMs",duration)
                put("fullyDecoded",true);put("listeningAssessed",false)
            }.toString())
        }.onFailure { recordFailure(run,it,"TRACE_WRITE_FAILED_AFTER_READY") }
    }

    private fun recordFailure(run: File, error: Throwable, reason: String) {
        runCatching {
            File(run,"failure.json").writeText(buildJsonObject {
                put("type",error.javaClass.simpleName);put("reason",reason);put("automaticResubmission",false)
            }.toString())
        }
    }
}

fun compositionRequest(snapshot: JsonObject): String {
    val summary=snapshot.getValue("summary").jsonObject
    val settings=snapshot.getValue("settings").jsonObject
    val segments=summary["positionSegments"]?.jsonArray
    val dwell=summary["dwellEvidence"]?.jsonArray.orEmpty()
    val facts=buildJsonObject {
        if(segments!=null) put("positionSegments",JsonArray(segments.map { element -> buildJsonObject {
            put("positionSequence",element.jsonObject.getValue("positionSequence"))
        } }))
        put("dwellEvidence",JsonArray(dwell.map { element -> buildJsonObject {
            val evidence=element.jsonObject
            put("position",evidence.getValue("position"));put("observationCount",evidence.getValue("observationCount"))
            put("observedRepeatedPositionSpanMs",evidence.getValue("observedRepeatedPositionSpanMs"))
        } }))
        put("continuousDistanceKm",summary.getValue("continuousDistanceKm"))
        put("gapCount",summary.getValue("gapCount"));put("returnObserved",summary.getValue("returnObserved"))
        put("timeMeaning",summary.getValue("timeMeaning"));put("environment","SIMULATED");put("acquisition","SDK")
    }
    val vocal=settings.string("lyricMode")=="VOCAL"
    val distance=summary.getValue("continuousDistanceKm").jsonPrimitive.double
    require(distance.isFinite() && distance>=0)
    val distanceText=java.math.BigDecimal.valueOf(distance).stripTrailingZeros().toPlainString()
    val returnLine=if(summary.getValue("returnObserved").jsonPrimitive.boolean) "처음과 마지막 관측 위치가 같은 기록" else "마지막 남긴 자리에서 기록을 마쳐"
    val style=when(settings.string("genre")) {
        "어쿠스틱" -> "acoustic indie folk with warm piano and guitar"
        "일렉트로닉" -> "melodic electronic"
        else -> "gentle indie pop"
    }
    val lyrics="""[Verse]
기록이 시작되고 길이 이어져
관측된 자리들을 차례로 지나
[Chorus]
관측된 거리 $distanceText 킬로미터
$returnLine
비어 있는 시간은 빈 채로 두고
지나온 순서대로 선율을 이어
[Outro]
마지막 기록에 이 노래를 남겨
""".trimIndent()
    val direction=if(vocal) "Sing Korean vocals using ONLY these supplied lyrics verbatim (a factual template, no separate lyrics AI call):\n$lyrics" else "Instrumental only: no vocals, spoken words, humming or vocal samples."
    val continuity=if(segments==null) "Segment evidence is unavailable: continuity and repeated-position duration are unknown. Do not infer a continuous route or pauses from legacy repeated labels."
        else "Develop only within each listed position segment. Boundaries between segments remain unknown; do not join segments into a continuous route."
    val repeatedPosition=if(segments!=null && dwell.isNotEmpty()) "You may soften the texture only using supplied dwellEvidence. observedRepeatedPositionSpanMs is an observed span of consecutive exact repeated positions, not proof of a stop, visit or dwell."
        else "There is no supported repeated-position duration; do not infer a stop, visit or dwell from repeated position labels."
    val prompt="""Create a complete original $style song around 90 seconds from a completed journey.
This is a post-drive reflection of an accelerated synthetic emulator journey, received through real Pleos SDKs.
Use a sparse opening and preserve only the supported observation order, not literal real-time driving speed.
$continuity
$repeatedPosition
returnObserved means the first and last observed coordinate matched; it does not prove a continuous return across gaps.
If that observation is true, recalling the opening texture is a creative choice, not a claim about an unobserved route.
This is creative musical interpretation, NOT measured driver emotion. Do not invent places, people, relationships, weather or events in gaps.
Distance is only continuous observed cumulative-distance differences, not an invented full-route distance. Observation spans are NOT driving duration.
Unknown intervals remain unknown. No named artist imitation. Do not add any unobserved facts.
SDK-derived facts (no exact coordinates, absolute odometer, IDs or raw hash):
$facts
$direction
""".trimIndent()
    return buildJsonObject { put("model","lyria-3.5");put("input",prompt) }.toString()
}

class AudioValidator {
    fun validate(file: File, run: File): Long {
        require(file.length() in 1024..20L*1024*1024)
        val progress=File(run,"decode-progress.txt")
        val process=ProcessBuilder(System.getenv("TODAYSTRACK_FFMPEG") ?: error("FFmpeg path required"),
            "-v","error","-i",file.absolutePath,"-progress","pipe:1","-nostats","-f","null","-")
            .redirectOutput(progress).redirectError(File(run,"decode-error.txt")).start()
        if(!process.waitFor(60,TimeUnit.SECONDS)) { process.destroyForcibly();error("Decode timeout") }
        check(process.exitValue()==0) { "Decode failed" }
        val duration=progress.readLines().filter { it.startsWith("out_time_us=") }.mapNotNull { it.substringAfter('=').toLongOrNull() }.maxOrNull()?.div(1000) ?: error("Missing decoded duration")
        require(duration>0)
        return duration
    }
}
