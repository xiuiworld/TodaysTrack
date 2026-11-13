package dev.todaystrack.server.music

import dev.todaystrack.music.*
import dev.todaystrack.server.DeviceAuth
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

fun Route.musicRoutes(service: MusicService,auth: DeviceAuth) {
    route("/v2/music") {
        suspend fun ApplicationCall.principal(body: ByteArray): MusicPrincipal? {
            if(request.queryParameters.names().isNotEmpty()) { respond(HttpStatusCode.BadRequest);return null }
            val headers=listOf("X-Device-Id","X-Timestamp","X-Nonce","X-Signature").associateWith { request.headers[it] ?: "" }
            return auth.authenticatePrincipal(request.httpMethod.value,request.path(),body,headers) ?: run {
                respondText("{\"error\":\"UNAUTHORIZED\"}",ContentType.Application.Json,HttpStatusCode.Unauthorized);null }
        }
        suspend fun ApplicationCall.result(limit: Int=2*1024*1024,action: (MusicPrincipal,ByteArray)->JsonObject) {
            try {
                val packet=receiveChannel().readRemaining(limit.toLong()+1)
                val bytes=ByteArray(packet.remaining.toInt()).also { packet.readFully(it) }
                if(bytes.size>limit) { respond(HttpStatusCode.PayloadTooLarge);return }
                val p=principal(bytes) ?: return
                val value=withContext(Dispatchers.IO) { action(p,bytes) }
                response.header(HttpHeaders.CacheControl,"private, no-store")
                respondText(canonical(value),ContentType.Application.Json)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) {
                val reason=e.message?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,100}")) } ?: "INVALID_INPUT"
                val status=when {
                    reason.endsWith("NOT_FOUND") -> HttpStatusCode.NotFound
                    reason in setOf("MEDIA_STORAGE_KEY_REQUIRED","RESULT_STORAGE_KEY_REQUIRED","MEDIA_DECODER_UNAVAILABLE") -> HttpStatusCode.ServiceUnavailable
                    e is IllegalStateException -> HttpStatusCode.Conflict
                    reason=="IDEMPOTENCY_CONFLICT" -> HttpStatusCode.Conflict
                    else -> HttpStatusCode.BadRequest
                }
                respondText(buildJsonObject { put("error",reason);put("automaticResubmission",false) }.toString(),ContentType.Application.Json,status)
            }
        }
        suspend fun ApplicationCall.jsonResult(action: (MusicPrincipal,JsonObject)->JsonObject)=result { p,b->action(p,musicJson.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject) }
        get("/capabilities") { call.result(0) { _,_->buildJsonObject {
            put("schemaVersion",2);put("featureVersion","features-v3");put("planVersion","composition-v2");put("analysisVersion",MediaAnalysisRequest.VERSION);put("requestVersion",MusicRequest.VERSION)
            put("analysisModel","gemini-3.8-flash");put("musicModel","lyria-3.5");put("dispatchEnabled",service.policy.enabled())
            put("manualKinds",JsonArray(MediaKind.entries.map { JsonPrimitive(it.name) }));put("automaticCaptureAllowed",false)
            put("maxMediaCount",30);put("maxImageBytes",MediaLimits().imageBytes);put("maxAudioVideoBytes",MediaLimits().audioVideoBytes);put("maxSelectedDurationMs",MediaLimits().maxDurationMs)
            put("genres",JsonArray(GenreCatalog.palettes.keys.map(::JsonPrimitive)));put("listeningVerified",false)
        } } }
        post("/media") { call.jsonResult { p,b->service.registerMedia(p,readMediaSelection(b)) } }
        put("/media/{id}/content") { call.result(64*1024*1024) { p,b->service.uploadMedia(p,call.parameters["id"]!!,b) } }
        get("/media/{id}") { call.result(0) { p,_->service.mediaStatus(p,call.parameters["id"]!!) } }
        delete("/media/{id}") { call.result(0) { p,_->service.revokeMedia(p,call.parameters["id"]!!) } }
        post("/inputs") { call.jsonResult(service::createInput) }
        post("/inputs/import-legacy") { call.jsonResult { p,b->b.only("snapshotId","intentId");service.importLegacy(p,b.text("snapshotId"),b.text("intentId")) } }
        get("/inputs/{id}") { call.result(0) { p,_->service.input(p,call.parameters["id"]!!) } }
        get("/inputs/{id}/plan") { call.result(0) { p,_->service.preview(p,call.parameters["id"]!!) } }
        get("/inputs/{id}/materials") { call.result(0) { p,_->service.materials(p,call.parameters["id"]!!) } }
        delete("/inputs/{id}") { call.result(0) { p,_->service.deleteInput(p,call.parameters["id"]!!) } }
        post("/quotes") { call.jsonResult { p,b->b.only("inputId","analysisOnly");service.quote(p,b.text("inputId"),b["analysisOnly"]?.jsonPrimitive?.boolean ?: false) } }
        post("/jobs") { call.jsonResult { p,b->b.only("inputId","quoteId","intentId");service.submit(p,b.text("inputId"),b.text("quoteId"),b.text("intentId")) } }
        get("/jobs/by-intent/{intent}") { call.result(0) { p,_->service.jobByIntent(p,call.parameters["intent"]!!) } }
        get("/jobs/{id}") { call.result(0) { p,_->service.job(p,call.parameters["id"]!!) } }
        post("/jobs/{id}/cancel") { call.jsonResult { p,b->b.only();val id=call.parameters["id"]!!;service.cancel(p,id);service.job(p,id) } }
        post("/jobs/{id}/resume-local-result") { call.jsonResult { p,b->b.only();service.resumeLocalResult(p,call.parameters["id"]!!) } }
        get("/jobs/{id}/audio") {
            val p=call.principal(byteArrayOf()) ?: return@get
            val id=call.parameters["id"]!!
            val result=runCatching { withContext(Dispatchers.IO) {
                val job=service.job(p,id);check(job.text("state")=="READY")
                val file=service.audioFile(p,id);check(file.isFile && file.length() in 1024..20L*1024*1024 && contentHash(file.readBytes())==job.text("sha256"));file
            } }.getOrNull()
            if(result==null) call.respond(HttpStatusCode.NotFound) else {
                call.response.header(HttpHeaders.CacheControl,"private, no-store");call.response.header(HttpHeaders.ContentType,"audio/mpeg");call.respondFile(result)
            }
        }
    }
}
