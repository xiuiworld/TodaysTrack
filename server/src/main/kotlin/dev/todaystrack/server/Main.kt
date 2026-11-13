package dev.todaystrack.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore

val json = Json { ignoreUnknownKeys = false }
fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
fun obj(vararg fields: Pair<String, JsonElement>) = JsonObject(mapOf(*fields))
fun primitive(value: String) = JsonPrimitive(value)

fun main(args: Array<String>) {
    val root = File(System.getenv("TODAYSTRACK_SERVER_STATE") ?: ".local").absoluteFile.apply { mkdirs() }
    val store = JobStore("jdbc:sqlite:${File(root,"server.db").path}", historicalMicros=300_000)
    if (args.firstOrNull() == "enroll") {
        val record=json.parseToJsonElement(File(args[1]).readText()).jsonObject
        store.enroll(record.string("deviceId"),record.string("publicKey"))
        println("Explicit development CAR enrollment saved; no token/private key displayed.")
        store.close(); return
    }
    val lockFile = RandomAccessFile(File(root,"server.lock"),"rw")
    val lock = lockFile.channel.tryLock() ?: error("Submission server already running")
    val password = System.getenv("TODAYSTRACK_TLS_PASSWORD")?.toCharArray() ?: error("TLS credential required")
    val keys=KeyStore.getInstance("PKCS12").apply { File(root,"tls/server.p12").inputStream().use { load(it,password) } }
    val auth=DeviceAuth(store)
    val worker=GenerationWorker(root,store)
    store.recover()
    embeddedServer(Netty, applicationEngineEnvironment {
        log=LoggerFactory.getLogger("TodaysTrackServer")
        sslConnector(keys,"server",{ password },{ password }) { host="127.0.0.1";port=8443 }
        module {
            val workerJob=launch(Dispatchers.IO) { worker.run() }
            environment.monitor.subscribe(ApplicationStopping) { workerJob.cancel();store.close(); lock.release();lockFile.close() }
            routing {
                route("/v1") {
                    suspend fun ApplicationCall.authorized(body: ByteArray): String? {
                        val headers=listOf("X-Device-Id","X-Timestamp","X-Nonce","X-Signature").associateWith { request.headers[it] ?: "" }
                        val owner=auth.authenticate(request.httpMethod.value,request.path(),body,headers)
                        if(owner==null) respondText("{\"error\":\"UNAUTHORIZED\"}",ContentType.Application.Json,HttpStatusCode.Unauthorized)
                        return owner
                    }
                    suspend fun ApplicationCall.mutation(action: (String,JsonObject)->JsonObject) {
                        try {
                            // Bounded read: never accept coordinates/uploads/provider URLs on these APIs.
                            val channel=receiveChannel()
                            val packet=channel.readRemaining(65_537)
                            val bytes=ByteArray(packet.remaining.toInt()).also { packet.readFully(it) }
                            if(bytes.size>65_536) { respond(HttpStatusCode.PayloadTooLarge);return }
                            val owner=authorized(bytes) ?: return
                            val body=json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                            val result=withContext(Dispatchers.IO) { action(owner,body) }
                            respondText(result.toString(),ContentType.Application.Json,HttpStatusCode.OK)
                        } catch(e: IllegalArgumentException) {
                            respondText("{\"error\":\"INPUT_OR_IDEMPOTENCY_CONFLICT\"}",ContentType.Application.Json,HttpStatusCode.Conflict)
                        } catch(e: IllegalStateException) {
                            respondText("{\"error\":\"REQUEST_HELD\"}",ContentType.Application.Json,HttpStatusCode.Conflict)
                        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                        catch(e: Exception) { respondText("{\"error\":\"REQUEST_FAILED\"}",ContentType.Application.Json,HttpStatusCode.BadRequest) }
                    }
                    post("/input-snapshots") { call.mutation { owner,body ->
                        require(body.keys==setOf("localIntentId","snapshot"))
                        val payload=body.string("snapshot")
                        validateSnapshot(owner,call.request.headers["X-Device-Id"]!!,payload)
                        val id=store.snapshot(owner,body.string("localIntentId"),sha256(payload.toByteArray()),payload)
                        obj("snapshotId" to primitive(id),"inputHash" to primitive(sha256(payload.toByteArray())))
                    } }
                    post("/generation-quotes") { call.mutation { owner,body ->
                        require(body.keys==setOf("snapshotId"))
                        val mode=worker.mode()
                        val cost=if(mode=="LIVE") 100_000L else 0L
                        check(store.reservedMicros()+cost<=5_000_000)
                        val quote=store.quote(owner,body.string("snapshotId"),cost,mode)
                        obj("quoteId" to primitive(quote),"mode" to primitive(mode),"maximumCostUsd" to JsonPrimitive(cost/1_000_000.0),"model" to primitive(if(mode=="LIVE") "lyria-3.5" else "existing-mp3-fixture"))
                    } }
                    post("/generation-jobs") { call.mutation { owner,body ->
                        require(body.keys==setOf("localIntentId","snapshotId","quoteId"))
                        val id=store.acceptQuote(owner,body.string("localIntentId"),body.string("snapshotId"),body.string("quoteId"))
                        jobResponse(store.job(owner,id)!!)
                    } }
                    get("/generation-jobs/by-intent/{localIntentId}") {
                        val owner=call.authorized(byteArrayOf()) ?: return@get
                        val job=store.jobByIntent(owner,call.parameters["localIntentId"]!!) ?: run { call.respond(HttpStatusCode.NotFound);return@get }
                        call.respondText(jobResponse(job).toString(),ContentType.Application.Json)
                    }
                    get("/generation-jobs/{id}") {
                        val owner=call.authorized(byteArrayOf()) ?: return@get
                        val job=store.job(owner,call.parameters["id"]!!) ?: run { call.respond(HttpStatusCode.NotFound);return@get }
                        call.respondText(jobResponse(job).toString(),ContentType.Application.Json)
                    }
                    get("/assets/{id}/audio") {
                        val owner=call.authorized(byteArrayOf()) ?: return@get
                        val id=call.parameters["id"]!!
                        val job=store.job(owner,id)
                        if(job?.get("state")!="READY") { call.respond(HttpStatusCode.NotFound);return@get }
                        // id comes from a looked-up job, never directly from a supplied path.
                        val file=File(root,"assets/${job.getValue("id")}.mp3")
                        call.response.header(HttpHeaders.CacheControl,"private, no-store")
                        call.respondFile(file)
                    }
                }
            }
        }
    }).start(wait=true)
}

fun jobResponse(job: Map<String,String?>): JsonObject = buildJsonObject {
    put("jobId",job.getValue("id"));put("state",job.getValue("state"));put("mode",job.getValue("mode"));put("inputHash",job.getValue("hash"))
    if(job["state"]=="READY") {
        put("assetPath","/v1/assets/${job.getValue("id")}/audio");put("sha256",job.getValue("asset_hash"))
        put("bytes",job.getValue("asset_bytes")!!.toLong());put("durationMs",job.getValue("duration_ms")!!.toLong())
    }
}

fun validateSnapshot(owner: String, device: String, payload: String) {
    require(payload.length<=60_000)
    val snapshot=json.parseToJsonElement(payload).jsonObject
    require(snapshot.keys==setOf("schemaVersion","ownerId","deviceId","sessionId","kind","zoneId","sourceManifest","sourceCoverageHash","rawHash","summaryVersion","summary","settings","acquisition","environment"))
    require(snapshot.string("ownerId")==owner && snapshot.string("deviceId")==device)
    require(snapshot.string("acquisition")=="SDK" && snapshot.string("environment")=="SIMULATED")
    val manifest=snapshot.getValue("sourceManifest").jsonArray
    require(manifest.size==1)
    val slice=manifest.single().jsonObject
    require(slice.keys==setOf("deviceId","sessionId","fromSeq","toSeqExclusive"))
    require(slice.string("deviceId")==device && slice.string("sessionId")==snapshot.string("sessionId"))
    val from=slice.getValue("fromSeq").jsonPrimitive.long
    val end=slice.getValue("toSeqExclusive").jsonPrimitive.long
    require(from>=0 && end>from)
    val canonical="coverage-v1\n$owner\n$device|${snapshot.string("sessionId")}|$from|$end"
    require(snapshot.string("sourceCoverageHash")==sha256(canonical.toByteArray()))
    val summary=snapshot.getValue("summary").jsonObject
    validateSummary(summary,from,end)
    val settings=snapshot.getValue("settings").jsonObject
    require(settings.keys==setOf("genre","lyricMode"))
    require(settings.string("genre") in setOf("잔잔한 팝","어쿠스틱","일렉트로닉"))
    require(settings.string("lyricMode") in setOf("INSTRUMENTAL","VOCAL"))
}

private fun validateSummary(summary: JsonObject, from: Long, end: Long) {
    val required=setOf("positionSequence","positionEvidenceSeqs","continuousDistanceKm","gapCount","returnObserved","epochObservationSpansMs","timeMeaning","unknown")
    val optional=setOf("positionSegments","positionUnknownBoundaries","dwellEvidence")
    require(summary.keys.containsAll(required) && summary.keys.all { it in required || it in optional })
    fun position(value: JsonElement): String = value.jsonPrimitive.content.also { require(it.matches(Regex("(?:[A-Z]|P[0-9]+)"))) }
    fun sequence(value: JsonObject): Pair<List<String>, List<Long>> {
        val positions=value.getValue("positionSequence").jsonArray.map(::position)
        val seqs=value.getValue("positionEvidenceSeqs").jsonArray.map { it.jsonPrimitive.long }
        require(positions.size==seqs.size && seqs.all { it>=from && it<end })
        require(seqs.zipWithNext().all { (a,b) -> b>a })
        return positions to seqs
    }
    val (flatPositions,flatSeqs)=sequence(summary)
    val distance=summary.getValue("continuousDistanceKm").jsonPrimitive.double
    require(distance.isFinite() && distance>=0)
    require(summary.getValue("gapCount").jsonPrimitive.int>=0)
    summary.getValue("returnObserved").jsonPrimitive.boolean
    val segments=summary["positionSegments"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    for(segment in segments) {
        require(segment.keys==setOf("positionSequence","positionEvidenceSeqs","epochId","bootId"))
        val positions=sequence(segment).first
        require(positions.isNotEmpty())
        if(listOf("epochId","bootId").any { key -> segment.string(key).let { it.isBlank() || it.equals("unknown",ignoreCase=true) } }) {
            require(positions.size==1) // Unknown time domains support observations, not continuity.
        }
    }
    if("positionSegments" in summary) {
        require(segments.flatMap { sequence(it).first }==flatPositions)
        require(segments.flatMap { sequence(it).second }==flatSeqs)
    }
    val boundaryReasons=setOf("LOCATION_GAP","EPOCH_CHANGE","BOOT_CHANGE","UNKNOWN_BOOT","UNKNOWN_EPOCH","INVALID_LOCATION","NON_MONOTONIC_TIME")
    for(element in summary["positionUnknownBoundaries"]?.jsonArray.orEmpty()) {
        val boundary=element.jsonObject
        require(boundary.keys==setOf("lastEvidenceSeq","firstEvidenceSeq","reasons","lastDurableSeq","firstAfterSeq"))
        val reasons=boundary.getValue("reasons").jsonArray
        require(reasons.isNotEmpty() && reasons.all { it.jsonPrimitive.content in boundaryReasons })
        for(key in listOf("lastEvidenceSeq","firstEvidenceSeq","lastDurableSeq","firstAfterSeq")) {
            val seq=boundary.getValue(key)
            require(seq==JsonNull || seq.jsonPrimitive.long>=0)
        }
    }
    for(element in summary["dwellEvidence"]?.jsonArray.orEmpty()) {
        val evidence=element.jsonObject
        require(evidence.keys==setOf("position","fromSeq","toSeq","observationCount","observedRepeatedPositionSpanMs","epochId","bootId"))
        val label=position(evidence.getValue("position"))
        val first=evidence.getValue("fromSeq").jsonPrimitive.long
        val last=evidence.getValue("toSeq").jsonPrimitive.long
        val count=evidence.getValue("observationCount").jsonPrimitive.int
        require(first>=from && last<end && last>first && count>=2)
        require(evidence.getValue("observedRepeatedPositionSpanMs").jsonPrimitive.long>=0)
        require(listOf("epochId","bootId").all { key -> evidence.string(key).let { it.isNotBlank() && !it.equals("unknown",ignoreCase=true) } })
        require(segments.any { segment ->
            if(segment.string("epochId")!=evidence.string("epochId") || segment.string("bootId")!=evidence.string("bootId")) false
            else {
                val (positions,seqs)=sequence(segment)
                val start=seqs.indexOf(first);val stop=seqs.indexOf(last)
                start>=0 && stop>start && stop-start+1==count && positions.subList(start,stop+1).all { it==label }
            }
        })
    }
}
