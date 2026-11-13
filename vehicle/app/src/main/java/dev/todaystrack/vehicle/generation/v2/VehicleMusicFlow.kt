package dev.todaystrack.vehicle.generation.v2

import dev.todaystrack.music.*
import kotlinx.serialization.json.*

data class LocalMusicIntent(val id: String,val sessionId: String,val payload: String,val inputHash: String,
    val state: String="LOCAL_PENDING",val jobId: String?=null,val mode: String="UNKNOWN",val errorCode: String?=null)
data class MusicReceipt(val intentId: String,val inputId: String?=null,val inputHash: String?=null,
    val acceptanceUncertain: Boolean=false,val plan: String?=null,val returnedLyrics: String?=null,
    val returnedStructure: String?=null,val integrity: String?=null,val serverRevision: Long=0)
data class SavedMusicAudio(val path: String,val sha256: String,val bytes: Long,val durationMs: Long)
interface VehicleMusicStore {
    suspend fun load(id: String): LocalMusicIntent
    suspend fun receipt(id: String): MusicReceipt?
    suspend fun save(intent: LocalMusicIntent,receipt: MusicReceipt)
    suspend fun ready(intent: LocalMusicIntent,receipt: MusicReceipt,audio: SavedMusicAudio,isNewAi: Boolean)
}
interface VehicleMusicApi {
    suspend fun call(method: String,path: String,body: JsonObject?=null): JsonObject
    suspend fun audio(path: String,jobId: String,sha256: String,bytes: Long): SavedMusicAudio
}
class MusicApiFailure(val status: Int,val reason: String): java.io.IOException("API_$status")
enum class FlowResult { COMPLETE, PENDING, HELD, RETRY }

object VehicleMusicInput {
    fun freeze(id: String,sessionId: String,deviceId: String,batch: ObservationBatch,
        preferences: MusicPreferences,materials: List<MaterialFact> = emptyList(),mediaIds: List<String> = emptyList()): LocalMusicIntent {
        require(listOf(id,sessionId,deviceId).all { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) })
        val features=FeatureExtractor().extract(batch)
        require(batch.observations.size>=2 && (features.sections.isNotEmpty() || features.metrics.values.any { it.evidence.isNotEmpty() })) { "MORE_OBSERVATIONS_REQUIRED" }
        require(materials.all { it.origin==FactOrigin.USER && it.attachmentId==null })
        require(mediaIds.size<=30 && mediaIds.distinct().size==mediaIds.size)
        val plan=CompositionPlanner().build(features,preferences,materials)
        val first=batch.observations.minOf { it.seq };val end=Math.addExact(batch.observations.maxOf { it.seq },1)
        val body=buildJsonObject {
            put("intentId",id);put("operation","INITIAL")
            put("coverage",buildJsonArray { add(buildJsonObject { put("deviceId",deviceId);put("sessionId",sessionId);put("fromSeq",first);put("toSeqExclusive",end) }) })
            put("features",featuresJson(features));put("preferences",preferencesJson(preferences))
            put("materials",JsonArray(materials.map(::materialJson)));put("mediaIds",JsonArray(mediaIds.map(::JsonPrimitive)));put("excludedMediaIds",JsonArray(emptyList()))
        }
        val envelope=buildJsonObject { put("schemaVersion",2);put("input",body);put("localPlan",planJson(plan));put("featureHash",contentHash(featuresJson(features))) }
        val payload=canonical(envelope);require(payload.toByteArray().size<=2*1024*1024) { "INPUT_SIZE_LIMIT" }
        return LocalMusicIntent(id,sessionId,payload,contentHash(payload.toByteArray(Charsets.UTF_8)))
    }
}
class VehicleMusicFlow(private val store: VehicleMusicStore,private val api: VehicleMusicApi) {
    suspend fun advance(id: String): FlowResult {
        var intent=store.load(id)
        var receipt=store.receipt(id) ?: MusicReceipt(id)
        if(intent.state=="AVAILABLE") return FlowResult.COMPLETE
        try {
            require(contentHash(intent.payload.toByteArray(Charsets.UTF_8))==intent.inputHash) { "LOCAL_INPUT_HASH_MISMATCH" }
            val envelope=musicJson.parseToJsonElement(intent.payload).jsonObject
            require(envelope.getValue("schemaVersion").jsonPrimitive.int==2)
            val body=envelope.getValue("input").jsonObject
            require(body.text("intentId")==id && id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            suspend fun persist(state: String,error: String?=null) {
                intent=intent.copy(state=state,errorCode=error);store.save(intent,receipt)
            }
            suspend fun adopt(job: JsonObject) {
                val jobId=remoteId(job.text("jobId"));val inputId=remoteId(job.text("inputId"));val hash=hash(job.text("inputHash"))
                if(receipt.inputId==null || receipt.inputHash==null) {
                    val remote=api.call("GET","/v2/music/inputs/$inputId")
                    val payload=remote.getValue("input").jsonObject
                    require(remote.text("inputHash")==hash && contentHash(payload)==hash) { "REMOTE_INPUT_HASH_MISMATCH" }
                    for(key in listOf("features","preferences","materials","coverage")) require(canonical(payload.getValue(key))==canonical(body.getValue(key))) { "REMOTE_INPUT_CONTENT_MISMATCH" }
                    require(payload.array("media").map { it.jsonObject.text("id") }==body.stringList("mediaIds")) { "REMOTE_INPUT_CONTENT_MISMATCH" }
                    receipt=receipt.copy(inputId=inputId,inputHash=hash)
                }
                require(inputId==receipt.inputId && hash==receipt.inputHash && (intent.jobId==null || intent.jobId==jobId)) { "REMOTE_JOB_IDENTITY_MISMATCH" }
                val revision=job.getValue("stateRevision").jsonPrimitive.long
                require(revision>=receipt.serverRevision) { "REMOTE_STATE_REVISION_REGRESSION" }
                val composition=job["compositionPlan"]?.takeUnless { it==JsonNull }
                if(composition!=null) require(composition is JsonObject && job.text("compositionPlanHash")==contentHash(composition)) { "REMOTE_PLAN_HASH_MISMATCH" }
                receipt=receipt.copy(acceptanceUncertain=false,serverRevision=revision,
                    plan=composition?.let(::canonical) ?: receipt.plan)
                intent=intent.copy(jobId=jobId,state=job.text("state"),mode=job.text("mode"),errorCode=job["error"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content)
                store.save(intent,receipt)
            }
            if(intent.jobId==null) {
                val existing=try { api.call("GET","/v2/music/jobs/by-intent/$id") } catch(e: MusicApiFailure) { if(e.status==404) null else throw e }
                if(existing!=null) adopt(existing) else {
                    if(receipt.acceptanceUncertain || intent.state !in setOf("LOCAL_PENDING","PREPARING","AUTH_REQUIRED","BILLING_HELD")) {
                        receipt=receipt.copy(acceptanceUncertain=true);persist("ACCEPTANCE_UNCERTAIN","ORIGINAL_JOB_NOT_FOUND");return FlowResult.HELD
                    }
                    persist("PREPARING")
                    if(receipt.inputId==null) {
                        val accepted=api.call("POST","/v2/music/inputs",body)
                        receipt=receipt.copy(inputId=remoteId(accepted.text("inputId")),inputHash=hash(accepted.text("inputHash")))
                        store.save(intent,receipt)
                    }
                    val preview=api.call("GET","/v2/music/inputs/${receipt.inputId}/plan")
                    require(preview.text("inputHash")==receipt.inputHash && preview.text("featureHash")==envelope.text("featureHash")) { "REMOTE_PLAN_INPUT_MISMATCH" }
                    require(preview.text("planHash")==contentHash(preview.getValue("plan"))) { "REMOTE_PLAN_HASH_MISMATCH" }
                    receipt=receipt.copy(plan=canonical(preview.getValue("plan")));store.save(intent,receipt)
                    val quote=api.call("POST","/v2/music/quotes",buildJsonObject { put("inputId",receipt.inputId) })
                    require(quote.text("inputHash")==receipt.inputHash && quote.text("inputId")==receipt.inputId) { "REMOTE_QUOTE_INPUT_MISMATCH" }
                    if(!quote.getValue("dispatchAllowed").jsonPrimitive.boolean) {
                        persist("BILLING_HELD",quote["holdReason"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content ?: "PAID_EXECUTION_DISABLED")
                        return FlowResult.HELD
                    }
                    receipt=receipt.copy(acceptanceUncertain=true)
                    persist("ACCEPTANCE_UNCERTAIN") // Atomic store commit precedes the acceptance POST.
                    val job=api.call("POST","/v2/music/jobs",buildJsonObject { put("inputId",receipt.inputId);put("quoteId",quote.text("quoteId"));put("intentId",id) })
                    adopt(job)
                }
            }
            val job=api.call("GET","/v2/music/jobs/${remoteId(intent.jobId!!)}")
            adopt(job)
            if(intent.state=="READY") {
                val jobId=intent.jobId!!
                val expectedPath="/v2/music/jobs/$jobId/audio"
                require(job.text("assetPath")==expectedPath) { "UNEXPECTED_ASSET_PATH" }
                val sha=hash(job.text("sha256"));val bytes=job.getValue("bytes").jsonPrimitive.long
                require(bytes in 1024..20L*1024*1024)
                val integrity=job.getValue("integrity").jsonObject
                val isNewAi=job.getValue("newAiGeneration").jsonPrimitive.boolean
                require(intent.mode in setOf("LIVE","LOCAL_TEST") && isNewAi==(intent.mode=="LIVE")) { "RESULT_PROVENANCE_MISMATCH" }
                require(!isNewAi || integrity.getValue("fullyDecoded").jsonPrimitive.boolean) { "RESULT_PROVENANCE_MISMATCH" }
                fun returned(key: String)=job.getValue(key).jsonPrimitive.content.also { require(it.length<=256_000) { "RETURNED_TEXT_LIMIT" } }
                receipt=receipt.copy(returnedLyrics=returned("returnedLyrics"),returnedStructure=returned("returnedStructure"),integrity=canonical(integrity))
                store.save(intent,receipt)
                val audio=api.audio(expectedPath,jobId,sha,bytes)
                require(audio.sha256==sha && audio.bytes==bytes && audio.durationMs>0) { "LOCAL_AUDIO_INTEGRITY" }
                intent=intent.copy(state="AVAILABLE",errorCode=null)
                store.ready(intent,receipt,audio,isNewAi)
                return FlowResult.COMPLETE
            }
            return if(intent.state in setOf("QUEUED","PREPARING","SUBMITTING","VALIDATING")) FlowResult.PENDING else FlowResult.HELD
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) {
            val reason=(e as? MusicApiFailure)?.reason?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,100}")) }
                ?: e.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,100}")) } ?: e.javaClass.simpleName
            val state=when {
                receipt.acceptanceUncertain -> "ACCEPTANCE_UNCERTAIN"
                e is MusicApiFailure && e.status in setOf(401,403) -> "AUTH_REQUIRED"
                e is MusicApiFailure && e.status in 400..499 -> "REQUEST_HELD"
                e is IllegalArgumentException -> "REQUEST_HELD"
                else -> if(intent.jobId!=null) "TRANSPORT_PENDING" else "PREPARING"
            }
            store.save(intent.copy(state=state,errorCode=reason),receipt)
            return if(e is java.io.IOException && (e !is MusicApiFailure || e.status>=500)) FlowResult.RETRY else FlowResult.HELD
        }
    }
    private fun remoteId(value: String)=value.also { require(it.matches(Regex("[a-f0-9-]{36}"))) { "INVALID_REMOTE_ID" } }
    private fun hash(value: String)=value.also { require(it.matches(Regex("[a-f0-9]{64}"))) { "INVALID_REMOTE_HASH" } }
}
