package dev.todaystrack.server.music

import dev.todaystrack.music.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

data class MusicPrincipal(val ownerId: String,val deviceId: String,val role: DeviceRole)

class MusicService(val root: File,val policy: ExecutionPolicy=ExecutionPolicy(),val storageKey: ByteArray?=null,
    val mediaProcessor: MediaProcessor=SafeMediaProcessor(File(root,"v2-scratch"))): AutoCloseable {
    init { require(root.isDirectory || root.mkdirs()) }
    val store=MusicStore(root,policy.testing)
    init { store.recover() }
    private val vault by lazy { MediaVault(File(root,"v2-media"),storageKey ?: error("MEDIA_STORAGE_KEY_REQUIRED")) }
    fun cancel(p: MusicPrincipal,id: String) { store.cancel(p.ownerId,id) }
    fun resumeLocalResult(p: MusicPrincipal,id: String): JsonObject {
        store.transaction {
            val j=store.job(p.ownerId,id) ?: error("JOB_NOT_FOUND")
            inputRecord(p,j.getValue("input_id")!!)
            check(j["state"] in setOf("FAILED_VALIDATION","MATERIAL_REVIEW_REQUIRED","RECONCILING")) { "LOCAL_RESUME_NOT_ALLOWED" }
            val saved=store.attempts(id).filter { File(root,"v2-responses/${it.getValue("id")}.enc").isFile }
            check(saved.isNotEmpty()) { "NO_DURABLE_RESULT_NO_AUTOMATIC_RESEND" }
            for(a in saved) store.execute("UPDATE v2_attempts SET state='RESULT_SAVED' WHERE id=?",a.getValue("id"))
            store.state(id,"PREPARING")
        }
        return job(p,id)
    }
    fun audioFile(p: MusicPrincipal,id: String): File {
        val j=store.job(p.ownerId,id) ?: error("JOB_NOT_FOUND")
        return File(root,"v2-assets/${j.getValue("id")}.mp3")
    }
    fun registerMedia(p: MusicPrincipal,s: MediaSelection): JsonObject {
        principal(p);MediaSelectionValidator.validate(s)
        check(storageKey?.size==32) { "MEDIA_STORAGE_KEY_REQUIRED" }
        val id=UUID.randomUUID().toString()
        store.execute("INSERT INTO v2_media(id,owner,selection,state,revision,created_ms) VALUES(?,?,?,'REGISTERED',?,?)",
            id,p.ownerId,canonical(mediaSelectionJson(s)),s.consent.revision,System.currentTimeMillis())
        return mediaStatus(p,id)
    }
    fun uploadMedia(p: MusicPrincipal,id: String,bytes: ByteArray): JsonObject {
        val record=mediaRecord(p,id);val s=readMediaSelection(musicJson.parseToJsonElement(record.getValue("selection")!!).jsonObject)
        MediaSelectionValidator.validate(s)
        require(bytes.size.toLong()==s.bytes && contentHash(bytes)==s.sha256) { "MEDIA_CONTENT_HASH_MISMATCH" }
        if(record["state"] in setOf("UPLOADED","ANALYZED") && vault.exists(id)) return mediaStatus(p,id)
        check(record["state"] in setOf("REGISTERED","UPLOADED","ANALYZED")) { "MEDIA_UPLOAD_NOT_ALLOWED" }
        val processed=mediaProcessor.process(bytes,s)
        store.transaction {
            val current=mediaRecord(p,id)
            check(current["revision"]==record["revision"] && current["state"] in setOf("REGISTERED","UPLOADED","ANALYZED")) { "MEDIA_CONSENT_CHANGED" }
            vault.put(id,processed.bytes)
            store.execute("UPDATE v2_media SET state='UPLOADED',derivative_hash=?,mime=?,bytes=?,duration_ms=?,cleanup_state='LOCAL_DERIVATIVE_ONLY' WHERE id=? AND owner=?",
                contentHash(processed.bytes),processed.mime,processed.bytes.size,processed.durationMs,id,p.ownerId)
        }
        return mediaStatus(p,id)
    }
    fun mediaBytes(p: MusicPrincipal,id: String): ByteArray {
        val r=mediaRecord(p,id);check(r["state"] in setOf("UPLOADED","ANALYZED")) { "MEDIA_REUPLOAD_REQUIRED" }
        check(vault.exists(id)) { "MEDIA_REUPLOAD_REQUIRED" }
        return vault.get(id).also { check(contentHash(it)==r["derivative_hash"]) { "MEDIA_DERIVATIVE_HASH_MISMATCH" } }
    }
    fun revokeMedia(p: MusicPrincipal,id: String): JsonObject {
        store.transaction {
            val r=store.media(p.ownerId,id) ?: error("MEDIA_NOT_FOUND")
            if(r["state"]!="REVOKED") {
                val selection=readMediaSelection(musicJson.parseToJsonElement(r.getValue("selection")!!).jsonObject)
                store.execute("UPDATE v2_media SET state='REVOKED',revision=revision+1,selection=?,cleanup_state='CLEANUP_PENDING' WHERE id=? AND owner=?",
                    canonical(mediaSelectionJson(selection.copy(consent=selection.consent.copy(revision=selection.consent.revision+1,revoked=true)))),id,p.ownerId)
                // Conservative same-account cache eviction cannot expose withdrawn analysis through another input.
                store.execute("DELETE FROM v2_analysis WHERE owner=? AND cache_key=?",p.ownerId,cacheKey(buildJsonObject { put("selection",mediaSelectionJson(selection));put("derivativeHash",r["derivative_hash"]?.let(::JsonPrimitive) ?: JsonNull) }))
                for(j in store.rows("SELECT j.* FROM v2_jobs j JOIN v2_inputs i ON j.input_id=i.id WHERE j.owner=? AND j.state NOT IN ('READY','CANCELED','DELETED')",p.ownerId)) {
                    val payload=musicJson.parseToJsonElement(store.input(p.ownerId,j.getValue("input_id")!!)!!.getValue("payload")!!).jsonObject
                    if(payload.array("media").any { it.jsonObject.text("id")==id && id !in payload.stringList("excludedMediaIds") }) store.cancelWithinTransaction(p.ownerId,j.getValue("id")!!)
                }
            }
        }
        runCatching { vault.delete(id);store.execute("UPDATE v2_media SET cleanup_state='LOCAL_DELETED_PROVIDER_RETENTION_UNKNOWN' WHERE id=? AND owner=?",id,p.ownerId) }
        return mediaStatus(p,id)
    }
    fun mediaStatus(p: MusicPrincipal,id: String): JsonObject {
        val r=store.media(p.ownerId,id) ?: error("MEDIA_NOT_FOUND")
        return buildJsonObject { put("mediaId",id);put("state",r.getValue("state"));put("revision",r.getValue("revision")!!.toLong())
            put("cleanupState",r.getValue("cleanup_state"));put("providerDeletionConfirmed",false)
            put("durationMs",r["duration_ms"]?.toLong()?.let(::JsonPrimitive) ?: JsonNull);put("mimeType",r["mime"]?.let(::JsonPrimitive) ?: JsonNull) }
    }
    private fun principal(p: MusicPrincipal) {
        require(p.ownerId.length in 1..200 && p.deviceId.length in 1..100)
    }
    fun createInput(principal: MusicPrincipal,body: JsonObject): JsonObject {
        principal(principal)
        body.only("intentId","operation","parentInputId","coverage","features","preferences","materials","mediaIds","excludedMediaIds","context")
        val intent=body.text("intentId");require(intent.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val operation=body.text("operation");require(operation in setOf("INITIAL","REGENERATE"))
        val parent=body["parentInputId"]?.jsonPrimitive?.content?.let { inputRecord(principal,it) }
        val coverage: JsonArray
        val featureJson: JsonObject
        if(principal.role==DeviceRole.PHONE) {
            require(operation=="REGENERATE" && parent!=null && "features" !in body && "coverage" !in body) { "CAR_FIRST_GENERATION_REQUIRED" }
            check(store.hasReady(principal.ownerId,parent.getValue("coverage_hash")!!)) { "FIRST_SUCCESS_REQUIRED" }
            val old=musicJson.parseToJsonElement(parent.getValue("payload")!!).jsonObject
            coverage=old.array("coverage");featureJson=old.getValue("features").jsonObject
        } else {
            if(operation=="REGENERATE") require(parent!=null) { "PARENT_INPUT_REQUIRED" }
            if(parent!=null && "features" !in body && "coverage" !in body) {
                val old=musicJson.parseToJsonElement(parent.getValue("payload")!!).jsonObject
                coverage=old.array("coverage");featureJson=old.getValue("features").jsonObject
            } else {
                coverage=normalizeCoverage(body.array("coverage"),principal.deviceId)
                featureJson=body.getValue("features").jsonObject
            }
        }
        val features=readFeatures(featureJson)
        validateEvidenceCoverage(features,coverage)
        val preferences=readPreferences(body.getValue("preferences").jsonObject)
        val materials=body.array("materials").map { readMaterial(it.jsonObject).also { m ->
            require(m.origin==FactOrigin.USER && m.attachmentId==null) { "ONLY_USER_MEMOS_ACCEPTED" }
        } }+(body["context"]?.jsonArray.orEmpty().also { require(it.size<=20) }.map { readContext(it.jsonObject).material() })
        val mediaIds=body.stringList("mediaIds").also { require(it.size<=30 && it.distinct().size==it.size) }
        val excluded=body["excludedMediaIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        require(excluded.all { it in mediaIds })
        val media=mediaIds.map { id->
            val record=mediaRecord(principal,id)
            val selection=readMediaSelection(musicJson.parseToJsonElement(record.getValue("selection")!!).jsonObject)
            MediaSelectionValidator.validate(selection)
            if(id !in excluded) check(record["state"] in setOf("UPLOADED","ANALYZED")) { "MEDIA_NOT_READY" }
            buildJsonObject { put("id",id);put("revision",selection.consent.revision);put("selection",mediaSelectionJson(selection));put("derivativeHash",record["derivative_hash"]?.let(::JsonPrimitive) ?: JsonNull) }
        }
        val direct=media.map { it.jsonObject }.filter { it.text("id") !in excluded && readMediaSelection(it.getValue("selection").jsonObject).consent.directMusicImage }
        require(direct.size<=10 && direct.sumOf { mediaRecord(principal,it.text("id"))["bytes"]?.toLong() ?: 0 }<=64L*1024*1024) { "DIRECT_IMAGE_LIMIT_SELECT_FEWER_IMAGES" }
        CompositionPlanner().build(features,preferences,materials) // validate preferences and user material before accepting.
        val canonicalCoverage=coverage.joinToString("\n") { e -> val c=e.jsonObject
            "${c.text("deviceId")}|${c.text("sessionId")}|${c.getValue("fromSeq").jsonPrimitive.long}|${c.getValue("toSeqExclusive").jsonPrimitive.long}" }
        val coverageHash=contentHash("coverage-v1\n${principal.ownerId}\n$canonicalCoverage".toByteArray(Charsets.UTF_8))
        val payload=buildJsonObject {
            put("schemaVersion",2);put("operation",operation);put("coverage",coverage);put("sourceCoverageHash",coverageHash)
            put("features",featuresJson(features));put("preferences",preferencesJson(preferences));put("materials",JsonArray(materials.map(::materialJson)))
            put("media",JsonArray(media));put("excludedMediaIds",JsonArray(excluded.sorted().map(::JsonPrimitive)))
            put("parentInputId",parent?.get("id")?.let(::JsonPrimitive) ?: JsonNull)
        }
        val encoded=canonical(payload);require(encoded.toByteArray(Charsets.UTF_8).size<=2*1024*1024) { "INPUT_SIZE_LIMIT" }
        val hash=contentHash(payload)
        val id=store.transaction {
            check(!store.deleted(principal.ownerId,coverageHash) && !store.deletedCoverage(principal.ownerId,coverage)) { "INPUT_DELETED" }
            store.row("SELECT * FROM v2_inputs WHERE owner=? AND intent=?",principal.ownerId,intent)?.let { old ->
                require(old["hash"]==hash) { "IDEMPOTENCY_CONFLICT" };return@transaction old.getValue("id")!!
            }
            UUID.randomUUID().toString().also { newId->store.execute("INSERT INTO v2_inputs(id,owner,intent,hash,payload,coverage_hash) VALUES(?,?,?,?,?,?)",
                newId,principal.ownerId,intent,hash,encoded,coverageHash) }
        }
        return buildJsonObject { put("inputId",id);put("inputHash",hash);put("sourceCoverageHash",coverageHash);put("schemaVersion",2) }
    }
    internal fun inputRecord(p: MusicPrincipal,id: String): Map<String,String?> {
        principal(p)
        val input=store.input(p.ownerId,id) ?: error("INPUT_NOT_FOUND")
        check(input["state"]=="ACTIVE" && !store.deleted(p.ownerId,input.getValue("coverage_hash")!!) &&
            !store.deletedCoverage(p.ownerId,musicJson.parseToJsonElement(input.getValue("payload")!!).jsonObject.array("coverage"))) { "INPUT_DELETED" }
        return input
    }
    fun input(principal: MusicPrincipal,id: String): JsonObject {
        val record=inputRecord(principal,id)
        return buildJsonObject { put("inputId",id);put("inputHash",record.getValue("hash"));put("input",musicJson.parseToJsonElement(record.getValue("payload")!!)) }
    }
    internal fun mediaRecord(p: MusicPrincipal,id: String)=store.media(p.ownerId,id)?.also { check(it["state"]!="REVOKED") { "MEDIA_REVOKED" } } ?: error("MEDIA_NOT_FOUND")
    internal fun cacheKey(media: JsonObject)=contentHash(buildJsonObject {
        put("selection",media.getValue("selection"));put("derivativeHash",media["derivativeHash"] ?: JsonNull);put("model","gemini-3.8-flash");put("analysisVersion","media-analysis-v1");put("processingVersion","selected-derivative-v1-image2048-audio24k96k-video360p2fps")
    })
    fun quote(principal: MusicPrincipal,inputId: String,analysisOnly: Boolean=false): JsonObject {
        val record=inputRecord(principal,inputId);val payload=musicJson.parseToJsonElement(record.getValue("payload")!!).jsonObject
        validateActiveMedia(principal,payload)
        val excluded=payload.stringList("excludedMediaIds")
        val keys=payload.array("media").map { it.jsonObject }.filter { it.text("id") !in excluded }.map(::cacheKey).distinct()
        val pending=keys.count { store.cache(principal.ownerId,it)==null }
        val analysisBound=if(policy.analysisMicros>0) policy.analysisMicros else PricingBound.analysisMicros
        val musicBound=if(policy.musicMicros>0) policy.musicMicros else PricingBound.musicMicros
        if(analysisOnly) check(pending>0) { "NO_ANALYSIS_NEEDED_USE_PLAN_PREVIEW" }
        val maximum=Math.addExact(Math.multiplyExact(analysisBound,pending.toLong()),if(analysisOnly) 0 else musicBound)
        val id=UUID.randomUUID().toString()
        val quote=buildJsonObject {
            put("quoteId",id);put("inputId",inputId);put("inputHash",record.getValue("hash"));put("analysisCount",pending)
            put("maximumRequests",pending+if(analysisOnly) 0 else 1);put("maximumCostMicros",maximum);put("authorizationHash",policy.hash());put("analysisOnly",analysisOnly)
            put("dispatchAllowed",policy.enabled());put("holdReason",if(policy.enabled()) JsonNull else JsonPrimitive("PAID_AUTHORIZATION_REQUIRED"))
            put("pricingVersion",PricingBound.version);put("costMeaning","CONSERVATIVE_RESERVATION_NOT_INVOICE; UNUSED_CAPACITY_NOT_AUTOMATICALLY_REFUNDED")
            put("analysisModel","gemini-3.8-flash");put("musicModel","lyria-3.5");put("expiresAtMs",System.currentTimeMillis()+300_000)
        }
        store.execute("INSERT INTO v2_quotes VALUES(?,?,?,?,?)",id,principal.ownerId,inputId,canonical(quote),quote.getValue("expiresAtMs").jsonPrimitive.long)
        return quote
    }
    fun submit(principal: MusicPrincipal,inputId: String,quoteId: String,intent: String): JsonObject {
        require(intent.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val input=inputRecord(principal,inputId)
        val payload=musicJson.parseToJsonElement(input.getValue("payload")!!).jsonObject
        if(principal.role==DeviceRole.PHONE) check(payload.text("operation")=="REGENERATE" && store.hasReady(principal.ownerId,input.getValue("coverage_hash")!!)) { "CAR_FIRST_GENERATION_REQUIRED" }
        validateActiveMedia(principal,payload)
        val quote=store.row("SELECT payload FROM v2_quotes WHERE id=? AND owner=? AND input_id=?",quoteId,principal.ownerId,inputId)
            ?.get("payload")?.let { musicJson.parseToJsonElement(it).jsonObject } ?: error("QUOTE_NOT_FOUND")
        store.jobByIntent(principal.ownerId,intent)?.let { old ->
            val kind=if(quote["analysisOnly"]?.jsonPrimitive?.boolean==true) "ANALYSIS" else "MUSIC"
            require(old["input_hash"]==input["hash"] && old["kind"]==kind) { "IDEMPOTENCY_CONFLICT" };return job(principal,old.getValue("id")!!)
        }
        check(quote.getValue("expiresAtMs").jsonPrimitive.long>=System.currentTimeMillis()) { "QUOTE_EXPIRED" }
        require(quote.text("inputHash")==input["hash"])
        return job(principal,store.reserveJob(principal.ownerId,input,intent,quote,policy) { validateActiveMedia(principal,payload) })
    }
    fun job(principal: MusicPrincipal,id: String): JsonObject {
        val row=store.job(principal.ownerId,id) ?: error("JOB_NOT_FOUND")
        return buildJsonObject {
            put("jobId",id);put("inputId",row.getValue("input_id"));put("inputHash",row.getValue("input_hash"));put("state",row.getValue("state"))
            put("stateRevision",row.getValue("revision")!!.toLong());put("stage",row["stage"]?.let(::JsonPrimitive) ?: JsonNull)
            put("mode",row.getValue("mode"));put("newAiGeneration",row["mode"]=="LIVE" && row["state"]=="READY")
            put("kind",row.getValue("kind"))
            row["plan"]?.let { put("compositionPlan",musicJson.parseToJsonElement(it));put("compositionPlanHash",contentHash(musicJson.parseToJsonElement(it))) }
            put("error",row["error"]?.let(::JsonPrimitive) ?: JsonNull);put("listeningAssessed",false)
            put("automaticResubmission",false);put("canCancel",row["state"] !in setOf("READY","CANCELED","DELETED"))
            put("attempts",JsonArray(store.attempts(id).map { a->buildJsonObject {
                put("stage",a.getValue("stage"));put("state",a.getValue("state"));put("requestHash",a.getValue("request_hash"));put("maximumCostMicros",a.getValue("cost")!!.toLong())
                put("providerCleanup",store.row("SELECT state FROM v2_cleanup WHERE attempt_id=?",a.getValue("id"))?.get("state") ?: "NOT_REQUESTED_OR_RESPONSE_UNKNOWN")
            } }))
            if(row["state"]=="READY") {
                put("assetPath","/v2/music/jobs/$id/audio");put("sha256",row.getValue("asset_hash"));put("bytes",row.getValue("asset_bytes")!!.toLong())
                put("durationMs",row.getValue("duration_ms")!!.toLong());put("integrity",musicJson.parseToJsonElement(row.getValue("integrity")!!))
                put("returnedLyrics",row["lyrics"] ?: "");put("returnedStructure",row["structure"] ?: "")
                put("lyricTextIsSingingVerification",false)
            }
        }
    }
    fun jobByIntent(p: MusicPrincipal,intent: String): JsonObject=job(p,store.jobByIntent(p.ownerId,intent)?.get("id") ?: error("JOB_NOT_FOUND"))
    fun deleteInput(p: MusicPrincipal,id: String): JsonObject {
        val current=store.input(p.ownerId,id) ?: error("INPUT_NOT_FOUND")
        if(current["state"]=="DELETED") return buildJsonObject { put("inputId",id);put("state","DELETED");put("legacyOriginalsChanged",false) }
        val remove=mutableListOf<String>()
        store.transaction {
            val input=inputRecord(p,id);val payload=musicJson.parseToJsonElement(input.getValue("payload")!!).jsonObject
            store.execute("INSERT OR IGNORE INTO v2_deleted_ranges VALUES(?,?)",p.ownerId,input.getValue("coverage_hash"))
            for(e in payload.array("coverage")) { val c=e.jsonObject
                store.execute("INSERT INTO v2_deleted_slices VALUES(?,?,?,?,?)",p.ownerId,c.text("deviceId"),c.text("sessionId"),c.getValue("fromSeq").jsonPrimitive.long,c.getValue("toSeqExclusive").jsonPrimitive.long) }
            for(j in store.rows("SELECT * FROM v2_jobs WHERE owner=?",p.ownerId)) {
                val old=store.input(p.ownerId,j.getValue("input_id")!!) ?: continue
                if(old["state"]!="ACTIVE") continue
                val oldPayload=musicJson.parseToJsonElement(old.getValue("payload")!!).jsonObject
                if(!store.deletedCoverage(p.ownerId,oldPayload.array("coverage"))) continue
                if(j["state"]!="READY") store.cancelWithinTransaction(p.ownerId,j.getValue("id")!!)
                store.execute("UPDATE v2_jobs SET state='DELETED',lyrics=NULL,structure=NULL,plan=NULL,revision=revision+1 WHERE id=?",j.getValue("id"))
                remove.add(j.getValue("id")!!)
            }
            for(old in store.rows("SELECT * FROM v2_inputs WHERE owner=? AND state='ACTIVE'",p.ownerId)) {
                val data=musicJson.parseToJsonElement(old.getValue("payload")!!).jsonObject
                if(store.deletedCoverage(p.ownerId,data.array("coverage"))) store.execute("UPDATE v2_inputs SET state='DELETED' WHERE id=?",old.getValue("id"))
            }
        }
        remove.forEach { job->File(root,"v2-assets/$job.mp3").delete();File(root,"v2-assets/$job.mp3.part").delete()
            store.attempts(job).forEach { a->runCatching { MediaVault(File(root,"v2-responses"),storageKey ?: error("RESULT_STORAGE_KEY_REQUIRED")).delete(a.getValue("id")!!) } } }
        return buildJsonObject { put("inputId",id);put("state","DELETED");put("legacyOriginalsChanged",false) }
    }
    /** Read-only bridge: preserves all v1 hashes/payloads/assets and imports no precision coordinates. */
    fun importLegacy(p: MusicPrincipal,snapshotId: String,intent: String): JsonObject {
        require(intent.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val snapshot=store.row("SELECT * FROM snapshots WHERE owner=? AND id=?",p.ownerId,snapshotId) ?: error("INPUT_NOT_FOUND")
        check(store.row("SELECT id FROM jobs WHERE owner=? AND hash=? AND state='READY' AND mode='LIVE'",p.ownerId,snapshot.getValue("hash"))!=null) { "FIRST_SUCCESS_REQUIRED" }
        val old=musicJson.parseToJsonElement(snapshot.getValue("payload")!!).jsonObject
        val summary=old.getValue("summary").jsonObject;val coverage=old.array("sourceManifest")
        val sections=summary["positionSegments"]?.jsonArray.orEmpty().mapIndexed { i,e->val s=e.jsonObject;val seqs=s.array("positionEvidenceSeqs").map { it.jsonPrimitive.long }
            FeatureSection("legacy-${i+1}",listOf(Evidence(Source.LOCATION,seqs.first(),seqs.last(),"frozen legacy summary; not re-extracted")),null,s.stringList("positionSequence")) }
        val f=JourneyFeatures(environment=Environment.valueOf(old.text("environment")),metrics=emptyMap(),sections=sections,stateRuns=emptyList(),gapReasons=listOf("LEGACY_CONTINUITY_NOT_REVALIDATED"),excluded=emptyMap(),sourceStatuses=emptyList(),unknown=listOf("legacy distance and spans have not been recomputed from original observations"),positionOrder=summary.stringList("positionSequence"),legacyReturnObserved=summary.getValue("returnObserved").jsonPrimitive.boolean)
        val payload=buildJsonObject { put("schemaVersion",2);put("operation","REGENERATE");put("coverage",coverage);put("sourceCoverageHash",old.getValue("sourceCoverageHash"))
            put("features",featuresJson(f));put("preferences",preferencesJson(readPreferences(old.getValue("settings").jsonObject)));put("materials",JsonArray(emptyList()));put("media",JsonArray(emptyList()));put("excludedMediaIds",JsonArray(emptyList()));put("parentInputId",JsonNull)
            put("legacyReference",buildJsonObject { put("snapshotId",snapshotId);put("inputHash",snapshot.getValue("hash"));put("interpretation","FROZEN_V1_RETAINED; V2_UNAVAILABLE_FEATURES_UNKNOWN") }) }
        val hash=contentHash(payload)
        val id=store.transaction {
            check(!store.deletedCoverage(p.ownerId,coverage)) { "INPUT_DELETED" }
            store.row("SELECT * FROM v2_inputs WHERE owner=? AND intent=?",p.ownerId,intent)?.let { require(it["hash"]==hash) { "IDEMPOTENCY_CONFLICT" };return@transaction it.getValue("id")!! }
            UUID.randomUUID().toString().also { store.execute("INSERT INTO v2_inputs(id,owner,intent,hash,payload,coverage_hash) VALUES(?,?,?,?,?,?)",it,p.ownerId,intent,hash,canonical(payload),old.text("sourceCoverageHash")) }
        }
        return input(p,id)
    }
    internal fun validateActiveMedia(p: MusicPrincipal,payload: JsonObject) {
        val excluded=payload.stringList("excludedMediaIds")
        for(element in payload.array("media")) {
            val media=element.jsonObject
            if(media.text("id") in excluded) continue
            val record=mediaRecord(p,media.text("id"))
            check(record["revision"]!!.toLong()==media.getValue("revision").jsonPrimitive.long) { "MEDIA_CONSENT_CHANGED" }
            check(record["derivative_hash"]==media["derivativeHash"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content) { "MEDIA_DERIVATIVE_CHANGED_CREATE_NEW_VERSION" }
            MediaSelectionValidator.validate(readMediaSelection(musicJson.parseToJsonElement(record.getValue("selection")!!).jsonObject))
        }
    }
    fun preview(principal: MusicPrincipal,inputId: String): JsonObject {
        val input=inputRecord(principal,inputId);val payload=musicJson.parseToJsonElement(input.getValue("payload")!!).jsonObject
        validateActiveMedia(principal,payload)
        val features=readFeatures(payload.getValue("features").jsonObject)
        val facts=payload.array("materials").map { readMaterial(it.jsonObject) }.toMutableList()
        val pending=mutableListOf<String>()
        for(e in payload.array("media")) {
            val m=e.jsonObject;if(m.text("id") in payload.stringList("excludedMediaIds")) continue
            val saved=store.cache(principal.ownerId,cacheKey(m))
            if(saved==null) pending.add(m.text("id")) else facts.addAll(analysisFacts(saved,m))
        }
        val plan=CompositionPlanner().build(features,readPreferences(payload.getValue("preferences").jsonObject),facts,payload.stringList("excludedMediaIds"))
        val request=MusicRequest.build(MusicPromptBuilder().build(plan,features))
        val missing=mutableListOf<String>()
        val images=mutableListOf<ProcessedMedia>()
        val imageManifest=payload.array("media").map { it.jsonObject }.filter { it.text("id") !in payload.stringList("excludedMediaIds") && readMediaSelection(it.getValue("selection").jsonObject).consent.directMusicImage }.map { m->
            val r=mediaRecord(principal,m.text("id"))
            runCatching { processedMedia(principal,m.text("id")) }.onSuccess { images.add(it) }.onFailure { missing.add(m.text("id")) }
            buildJsonObject { put("mediaId",m.text("id"));put("mimeType",r["mime"]);put("derivativeHash",r["derivative_hash"]);put("bytes",r["bytes"]?.toLong()?.let(::JsonPrimitive) ?: JsonNull) }
        }
        return buildJsonObject { put("inputHash",input.getValue("hash"));put("featureHash",contentHash(featuresJson(features)));put("planHash",contentHash(planJson(plan)))
            put("requestTextHash",contentHash(request));put("plan",planJson(plan));put("providerRequest",request)
            put("directImageManifest",JsonArray(imageManifest));put("reuploadMediaIds",JsonArray(missing.map(::JsonPrimitive)))
            put("requestReady",pending.isEmpty() && missing.isEmpty());put("actualRequestHash",if(pending.isEmpty() && missing.isEmpty()) JsonPrimitive(contentHash(MusicRequest.build(request.getValue("input").jsonPrimitive.content,images))) else JsonNull)
            put("pendingMediaIds",JsonArray(pending.map(::JsonPrimitive)));put("previewComplete",pending.isEmpty());put("completedLyricsAvailable",false) }
    }
    fun materials(principal: MusicPrincipal,inputId: String): JsonObject {
        val record=inputRecord(principal,inputId);val payload=musicJson.parseToJsonElement(record.getValue("payload")!!).jsonObject
        validateActiveMedia(principal,payload)
        return buildJsonObject {
            put("inputHash",record.getValue("hash"));put("userMaterials",payload.getValue("materials"))
            put("media",JsonArray(payload.array("media").map { element->val m=element.jsonObject
                val excluded=m.text("id") in payload.stringList("excludedMediaIds")
                val result=if(excluded) null else store.cache(principal.ownerId,cacheKey(m))
                buildJsonObject { put("mediaId",m.text("id"));put("excludedByUser",excluded);put("analysisModel","gemini-3.8-flash");put("analysisVersion",MediaAnalysisRequest.VERSION)
                    put("state",if(excluded) "EXCLUDED" else if(result==null) "PENDING_OR_HELD" else "MODEL_ANALYSIS_AVAILABLE_NOT_INDEPENDENTLY_VERIFIED")
                    put("facts",JsonArray(result?.let { analysisFacts(it,m).map(::materialJson) }.orEmpty()));put("uncertainties",result?.let { musicJson.parseToJsonElement(it).jsonObject.getValue("uncertainties") } ?: JsonNull)
                }
            }))
        }
    }
    override fun close()=store.close()
    internal fun cleanupDerivative(p: MusicPrincipal,id: String) {
        store.execute("UPDATE v2_media SET cleanup_state='LOCAL_CLEANUP_PENDING_PROVIDER_RETENTION_UNKNOWN' WHERE owner=? AND id=?",p.ownerId,id)
        vault.delete(id)
        store.execute("UPDATE v2_media SET cleanup_state='LOCAL_DELETED_STATELESS_REQUESTED_PROVIDER_RETENTION_UNKNOWN' WHERE owner=? AND id=?",p.ownerId,id)
    }
    internal fun processedMedia(p: MusicPrincipal,id: String): ProcessedMedia {
        val r=mediaRecord(p,id)
        return ProcessedMedia(mediaBytes(p,id),r.getValue("mime")!!,r["duration_ms"]?.toLong())
    }
}

private fun normalizeCoverage(array: JsonArray,deviceId: String): JsonArray {
    require(array.size in 1..1000)
    val slices=array.map { e->val s=e.jsonObject;s.only("deviceId","sessionId","fromSeq","toSeqExclusive")
        require(s.text("deviceId")==deviceId && s.text("sessionId").matches(Regex("[A-Za-z0-9_-]{1,100}")))
        dev.todaystrack.music.legacy.Slice(s.text("deviceId"),s.text("sessionId"),s.getValue("fromSeq").jsonPrimitive.long,s.getValue("toSeqExclusive").jsonPrimitive.long) }
    return JsonArray(dev.todaystrack.music.legacy.SourceCoverage.normalize(slices).map { s->buildJsonObject {
        put("deviceId",s.deviceId);put("sessionId",s.sessionId);put("fromSeq",s.fromSeq);put("toSeqExclusive",s.toSeqExclusive) } })
}
private fun validateEvidenceCoverage(f: JourneyFeatures,coverage: JsonArray) {
    val ranges=coverage.map { it.jsonObject.let { s-> "${s.text("deviceId")}/${s.text("sessionId")}" to (s.getValue("fromSeq").jsonPrimitive.long until s.getValue("toSeqExclusive").jsonPrimitive.long) } }
    val evidence=f.metrics.values.flatMap { it.evidence }+f.sections.flatMap { it.evidence }+f.stateRuns.flatMap { it.evidence }
    require(evidence.all { e->ranges.any { (ref,range)->(e.coverageRef==ref || e.coverageRef==null && coverage.size==1) && e.fromSeq in range && e.toSeq in range } }) { "EVIDENCE_OUTSIDE_COVERAGE" }
}

/** Analyzer origin is derived from the selected media kind, never trusted from model output. */
internal fun analysisFacts(result: String,media: JsonObject): List<MaterialFact> {
    val selection=readMediaSelection(media.getValue("selection").jsonObject)
    val response=musicJson.parseToJsonElement(result).jsonObject
    response.only("statements","uncertainties")
    val statements=response.array("statements");require(statements.size<=12)
    return statements.mapIndexed { index,e->val statement=e.jsonObject
        statement.only("text","category","fromMs","toMs","uncertainty")
        val category=statement.text("category")
        val expected=when(selection.kind) { MediaKind.VOICE_MEMO->setOf("TRANSCRIPT");MediaKind.PHOTO,MediaKind.VIDEO->setOf("SCENE");MediaKind.ENVIRONMENT_AUDIO->setOf("AUDIO");MediaKind.HUMMING->setOf("MUSIC_CONTOUR");else->emptySet() }
        require(category in expected) { "INVALID_ANALYSIS_CATEGORY" }
        val text=statement.text("text");require(text.length in 1..2000)
        val from=statement["fromMs"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.long
        val to=statement["toMs"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.long
        val selectedDuration=selection.toMs ?: selection.durationMs
        require((from==null && to==null) || (from!=null && to!=null && from>=0 && to>=from && selectedDuration!=null && to<=selectedDuration-(selection.fromMs ?: 0)))
        MaterialFact("media-${media.text("id")}-${index}",if(selection.kind==MediaKind.VOICE_MEMO) FactOrigin.USER else FactOrigin.MEDIA_INFERENCE,
            text,media.text("id"),from,to,statement["uncertainty"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content,
            selection.consent.lyricsAllowed)
    }
}
