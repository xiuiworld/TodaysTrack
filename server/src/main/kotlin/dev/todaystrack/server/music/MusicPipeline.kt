package dev.todaystrack.server.music

import dev.todaystrack.music.*
import dev.todaystrack.server.AudioValidator
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class AudioInspectionResult(val durationMs: Long,val fullyDecoded: Boolean)
fun interface AudioInspection { fun inspect(file: File): AudioInspectionResult }
class DecodingAudioInspection: AudioInspection {
    override fun inspect(file: File): AudioInspectionResult {
        val scratch=Files.createTempDirectory("todaystrack-audio-check-").toFile()
        try { return AudioInspectionResult(AudioValidator().validate(file,scratch),true) }
        finally { listOf("decode-progress.txt","decode-error.txt").forEach { File(scratch,it).delete() };scratch.delete() }
    }
}

/** One caller/worker lane. Each call performs at most one paid operation, never an automatic retry. */
class MusicPipeline(private val service: MusicService,private val transport: ModelTransport,
    private val inspect: AudioInspection=DecodingAudioInspection(),
    private val authorizationStillValid: ()->Boolean={ service.policy.testing || ExecutionPolicy.read(service.root).let { it.hash()==service.policy.hash() && it.enabled() } }) {
    private val store=service.store
    private val responses by lazy { MediaVault(File(service.root,"v2-responses"),service.storageKey ?: error("RESULT_STORAGE_KEY_REQUIRED")) }
    private val assets=File(service.root,"v2-assets").apply { mkdirs() }
    private fun responseExists(id: String)=File(service.root,"v2-responses/$id.enc").isFile
    @Synchronized fun processNext(): Boolean {
        val recovering=store.rows("SELECT * FROM v2_jobs WHERE state IN ('RECONCILING','PREPARING','VALIDATING','SUBMITTING') ORDER BY rowid")
            .firstOrNull { j->store.attempts(j.getValue("id")!!).any { it["state"]!="REVIEW_REQUIRED" && responseExists(it.getValue("id")!!) } }
        val job=recovering ?: store.row("SELECT * FROM v2_jobs WHERE state IN ('QUEUED','PREPARING') ORDER BY rowid LIMIT 1") ?: return false
        val id=job.getValue("id")!!;val principal=MusicPrincipal(job.getValue("owner")!!,"server",DeviceRole.CAR)
        var currentAttempt: String?=null
        var receivedResponse: String?=null
        try {
            service.inputRecord(principal,job.getValue("input_id")!!)
            val payload=musicJson.parseToJsonElement(store.input(principal.ownerId,job.getValue("input_id")!!)!!.getValue("payload")!!).jsonObject
            service.validateActiveMedia(principal,payload)
            val activeMedia=payload.array("media").map { it.jsonObject }.filter { it.text("id") !in payload.stringList("excludedMediaIds") }
            val saved=store.attempts(id).firstOrNull { it["state"]!="REVIEW_REQUIRED" && responseExists(it.getValue("id")!!) }
            if(saved!=null) {
                currentAttempt=saved.getValue("id")!!
                consume(principal,job,payload,activeMedia,saved,responses.get(currentAttempt).toString(Charsets.UTF_8))
                return true
            }
            check(service.storageKey?.size==32) { "RESULT_STORAGE_KEY_REQUIRED" }
            check(service.policy.enabled() && authorizationStillValid()) { "PAID_EXECUTION_DISABLED" }
            val pending=activeMedia.firstOrNull { store.cache(principal.ownerId,service.cacheKey(it))==null }
            if(pending==null && job["kind"]=="ANALYSIS") { store.state(id,"PLAN_READY");return true }
            val stage: String;val key: String;val request: String;val cost: Long
            if(pending!=null) {
                stage="ANALYSIS";key=service.cacheKey(pending);cost=service.policy.analysisMicros
                val selection=readMediaSelection(pending.getValue("selection").jsonObject)
                request=canonical(MediaAnalysisRequest.build(selection,service.processedMedia(principal,pending.text("id"))))
                require(request.toByteArray().size<=if(selection.kind in setOf(MediaKind.VOICE_MEMO,MediaKind.ENVIRONMENT_AUDIO,MediaKind.HUMMING)) 20*1024*1024 else 96*1024*1024) { "ANALYSIS_INLINE_SIZE_LIMIT" }
            } else {
                stage="MUSIC";key=job.getValue("input_hash")!!;cost=service.policy.musicMicros
                val preview=service.preview(principal,job.getValue("input_id")!!)
                check(preview.getValue("previewComplete").jsonPrimitive.boolean)
                val images=activeMedia.filter { readMediaSelection(it.getValue("selection").jsonObject).consent.directMusicImage }.map { service.processedMedia(principal,it.text("id")) }
                request=canonical(MusicRequest.build(preview.getValue("providerRequest").jsonObject.getValue("input").jsonPrimitive.content,images))
                require(request.toByteArray().size<=96*1024*1024) { "DIRECT_IMAGE_TOTAL_SIZE_LIMIT" }
                store.execute("UPDATE v2_jobs SET plan=?,request_hash=? WHERE id=?",canonical(preview.getValue("plan")),contentHash(request.toByteArray()),id)
            }
            val attempt=store.beginAttempt(id,stage,key,request,cost,service.policy)
            if(attempt==null) { store.state(id,"RECONCILING","EXISTING_DISPATCH_FENCE_NO_AUTOMATIC_RETRY");return true }
            currentAttempt=attempt
            val permit=DispatchPermit(store,attempt,service.policy,authorizationStillValid)
            service.validateActiveMedia(principal,payload)
            val response=transport.send(permit,request)
            receivedResponse=response
            check(store.jobById(id)?.get("state")=="SUBMITTING") { "RESULT_SUPPRESSED" }
            service.validateActiveMedia(principal,payload)
            responses.put(attempt,response.toByteArray(Charsets.UTF_8))
            consume(principal,job,payload,activeMedia,store.attempt(attempt)!!,response)
        } catch(e: Exception) {
            val now=store.jobById(id)?.get("state")
            if(now !in setOf("READY","CANCELED","DELETED")) {
                val stage=currentAttempt?.let { store.attempt(it)?.get("stage") }
                val state=when {
                    e is ProviderRejected && e.status in setOf(402,429) -> "BILLING_HELD"
                    e is ProviderRejected && e.status in 400..499 -> if(stage=="ANALYSIS") "MATERIAL_REVIEW_REQUIRED" else "FAILED_PROVIDER_REJECTION"
                    currentAttempt!=null && store.attempt(currentAttempt)?.get("state")=="SUBMITTING" && !responseExists(currentAttempt) -> "RECONCILING"
                    stage=="ANALYSIS" -> "MATERIAL_REVIEW_REQUIRED"
                    currentAttempt!=null -> "FAILED_VALIDATION"
                    e.message?.let { it.startsWith("MEDIA_") || it.startsWith("MATERIAL_") || it.startsWith("ANALYSIS_") || it.startsWith("DIRECT_IMAGE_") || it.startsWith("MUSIC_CONTEXT_") }==true -> "MATERIAL_REVIEW_REQUIRED"
                    else -> "BILLING_HELD"
                }
                store.state(id,state,if(stage=="ANALYSIS") "ANALYSIS_FAILED_EXPLICIT_EXCLUSION_OR_REVIEW_REQUIRED" else safeReason(e))
                if(e is ProviderRejected && e.status in 400..499 && currentAttempt!=null) store.execute("UPDATE v2_attempts SET state='PROVIDER_REJECTED',result=? WHERE id=?","{\"httpStatus\":${e.status}}",currentAttempt)
                if(e is ProviderRejected && e.status in setOf(402,429)) durableWrite(File(service.root,"billing-stop"),"Provider quota/billing rejection; no automatic resubmission".toByteArray())
            }
            if(now in setOf("CANCELED","DELETED")) {
                if(currentAttempt!=null && receivedResponse!=null) runCatching { cleanupProvider(currentAttempt,receivedResponse) }
                currentAttempt?.let { runCatching { responses.delete(it) } };File(assets,"$id.mp3").delete();File(assets,"$id.mp3.part").delete()
            } else if(currentAttempt!=null && responseExists(currentAttempt) && now!="READY") {
                store.execute("UPDATE v2_attempts SET state='REVIEW_REQUIRED' WHERE id=?",currentAttempt)
            }
        }
        return true
    }
    private fun consume(p: MusicPrincipal,job: Map<String,String?>,payload: JsonObject,media: List<JsonObject>,attempt: Map<String,String?>,response: String) {
        val id=job.getValue("id")!!;val attemptId=attempt.getValue("id")!!
        cleanupProvider(attemptId,response)
        if(attempt["stage"]=="ANALYSIS") {
            val target=media.first { service.cacheKey(it)==attempt["stage_key"] }
            val parsed=analysisResponse(response);analysisFacts(parsed,target)
            store.transaction {
                service.validateActiveMedia(p,payload)
                check(store.jobById(id)?.get("state") !in setOf("CANCELED","DELETED")) { "RESULT_SUPPRESSED" }
                store.cache(p.ownerId,service.cacheKey(target),parsed)
                store.execute("UPDATE v2_media SET state='ANALYZED',cleanup_state='STATELESS_REQUESTED_PROVIDER_RETENTION_UNKNOWN' WHERE owner=? AND id=?",p.ownerId,target.text("id"))
            }
            store.resultSaved(attemptId,parsed)
            if(!readMediaSelection(target.getValue("selection").jsonObject).consent.directMusicImage) runCatching { service.cleanupDerivative(p,target.text("id")) }
            responses.delete(attemptId)
        } else {
            val result=musicResponse(response)
            store.resultSaved(attemptId,"{\"durableResponse\":true}")
            store.state(id,"VALIDATING")
            val partial=File(assets,"$id.mp3.part");durableWrite(partial,result.audio)
            val integrity=inspect.inspect(partial);require(integrity.durationMs>0)
            val final=File(assets,"$id.mp3")
            Files.move(partial.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            store.transaction {
                service.inputRecord(p,job.getValue("input_id")!!);service.validateActiveMedia(p,payload)
                check(store.jobById(id)?.get("state")=="VALIDATING") { "RESULT_SUPPRESSED" }
                val record=buildJsonObject { put("fullyDecoded",integrity.fullyDecoded);put("fileHashVerified",true);put("lyricsSingingVerified",false);put("compositionAdherenceVerified",false);put("listeningAssessed",false) }
                store.execute("UPDATE v2_jobs SET state='READY',asset_hash=?,asset_bytes=?,duration_ms=?,integrity=?,lyrics=?,structure=?,revision=revision+1 WHERE id=?",
                    contentHash(result.audio),result.audio.size,integrity.durationMs,canonical(record),result.lyrics,result.structure,id)
                store.execute("UPDATE v2_attempts SET state='COMPLETE',result=? WHERE id=?",canonical(buildJsonObject { put("returnedText",result.returnedText);put("requestVersion",MusicRequest.VERSION);put("providerStorage","STATELESS_REQUESTED_RETENTION_UNKNOWN") }),attemptId)
            }
            responses.delete(attemptId)
            media.forEach { runCatching { service.cleanupDerivative(p,it.text("id")) } }
        }
    }
    private fun safeReason(e: Exception)=e.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,100}")) } ?: "LOCAL_PROCESSING_OR_TRANSPORT_HELD"
    private fun cleanupProvider(attemptId: String,response: String) {
        if(store.row("SELECT state FROM v2_cleanup WHERE attempt_id=?",attemptId)!=null) return
        store.execute("INSERT INTO v2_cleanup VALUES(?,'DELETE_REQUESTED_OR_STATELESS_CONFIRMATION_PENDING')",attemptId)
        val cleanup=runCatching { transport.cleanup(DispatchPermit(store,attemptId,service.policy,authorizationStillValid),response) }.getOrDefault("PROVIDER_DELETION_UNCONFIRMED")
        store.execute("UPDATE v2_cleanup SET state=? WHERE attempt_id=?",cleanup,attemptId)
    }
}
