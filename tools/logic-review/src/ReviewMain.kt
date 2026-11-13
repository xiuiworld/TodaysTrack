package dev.todaystrack.review

import dev.todaystrack.vehicle.capture.CapturePolicy
import dev.todaystrack.vehicle.journey.*
import dev.todaystrack.vehicle.generation.*
import dev.todaystrack.vehicle.storage.*
import dev.todaystrack.server.JobStore
import dev.todaystrack.server.compositionRequest
import dev.todaystrack.server.validateSnapshot
import dev.todaystrack.server.sha256
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
private fun JSONObject.doubleOrNull(key: String) = if (isNull(key)) null else getDouble(key)
private fun jsonArray(items: List<JSONObject>) = JSONArray(items)

fun main(args: Array<String>) {
    require(args.size == 2)
    val input = JSONObject(File(args[0]).readText())
    val cases = input.getJSONArray("cases")
    val result = JSONArray()
    for (i in 0 until cases.length()) {
        val c = cases.getJSONObject(i)
        result.put(runCatching { evaluate(c) }.getOrElse {
            JSONObject().put("id", c.getString("id")).put("error", it.javaClass.simpleName + ": " + it.message)
        })
    }
    File(args[1]).writeText(JSONObject().put("cases", result).toString(2))
}

private fun evaluate(c: JSONObject): JSONObject {
    val sessionJson = c.optJSONObject("session") ?: JSONObject()
    val session = CaptureSession(c.getString("id"), sessionJson.optString("kind", "TRIP"), "Asia/Seoul",
        sessionJson.optString("genre", "어쿠스틱"), sessionJson.optString("lyricMode", "VOCAL"),
        sessionJson.optLong("startedAtUtc", 100000), "SEALED")
    val raw = c.getJSONArray("events")
    val events = (0 until raw.length()).map { index ->
        val e = raw.getJSONObject(index)
        CaptureEvent(seq=e.getLong("seq"), eventId=e.optString("eventId", "review-${e.getLong("seq")}"),
            sessionId=session.id, epochId=e.getString("epochId"), bootId=e.getString("bootId"),
            kind=e.optString("kind", e.getString("source")), source=e.getString("source"),
            receivedAtUtc=e.getLong("receivedAtUtc"), receivedElapsedMs=e.getLong("receivedElapsedMs"),
            sourceTimestampMs=e.longOrNull("sourceTimestampMs"), sourceTimeBasis=e.optString("sourceTimeBasis", "SDK_UTC_MILLIS"),
            requestStartedElapsedMs=e.longOrNull("requestStartedElapsedMs"), latitude=e.doubleOrNull("latitude"),
            longitude=e.doubleOrNull("longitude"), odometerKm=e.doubleOrNull("odometerKm"),
            quality=e.getString("quality"), detail=if(e.isNull("detail")) null else e.getString("detail"),
            policyVersion=e.optString("policyVersion", "capture-v2"))
    }
    val gs = c.optJSONArray("gaps") ?: JSONArray()
    val gaps = (0 until gs.length()).map { i -> val g=gs.getJSONObject(i)
        CaptureGap("review-gap-$i",session.id,g.optString("epochId", "e1"),g.getString("sourceMask"),
            g.longOrNull("lastDurableSeq"),g.longOrNull("firstAfterSeq"),g.optString("reason", "REVIEW_INPUT")) }
    val facts = events.map { it.toSummaryFact() }
    val previousSourceTimes = mutableMapOf<String, Long>()
    val rows = events.zip(facts).sortedBy { it.first.seq }.map { (e,f) ->
        val usable = f.source == "LOCATION" && f.quality == "FRESH" && !f.sourceError &&
            f.lat?.let { it.isFinite() && it in -90.0..90.0 } == true && f.lon?.let { it.isFinite() && it in -180.0..180.0 } == true
        val freshCheck = if (e.source == "LOCATION") CapturePolicy().locationQuality(e.sourceTimestampMs,
            c.optJSONObject("epochStartsUtc")?.optLong(e.epochId, session.startedAtUtc) ?: session.startedAtUtc,
            e.receivedAtUtc, previousSourceTimes[e.epochId]) else null
        if(freshCheck=="FRESH") e.sourceTimestampMs?.let { previousSourceTimes[e.epochId]=it }
        JSONObject().put("seq",e.seq).put("source",e.source).put("storedQuality",e.quality)
            .put("summaryQuality",f.quality).put("capturePolicyDiagnostic",freshCheck ?: JSONObject.NULL)
            .put("positionIncluded",usable).put("reason",when {
                f.sourceError -> "SOURCE_ERROR"
                e.source != "LOCATION" -> "위치 대상 아님 · 거리 차분은 별도 표"
                usable -> "유효 위치 관측"
                f.quality != "FRESH" -> f.quality
                else -> "INVALID_COORDINATE"
            })
    }
    val boundaries=gaps.map { LocationGapBoundary(it.lastDurableSeq,it.firstAfterSeq,it.sourceMask) }
    val builder=JourneySummaryBuilder()
    val prefixDistances=events.sortedBy { it.seq }.indices.map { index ->
        builder.build(events.sortedBy { it.seq }.take(index+1).map { it.toSummaryFact() },gaps.size,boundaries).continuousDistanceKm
    }
    val sorted=events.sortedBy { it.seq }
    val distanceRows=sorted.mapIndexedNotNull { index,e -> if(e.source!="ODOMETER") null else {
        val older=sorted.take(index).lastOrNull { it.source=="ODOMETER" }
        val change=prefixDistances[index]-(prefixDistances.getOrNull(index-1) ?: 0.0)
        JSONObject().put("seq",e.seq).put("previousSeq",older?.seq ?: JSONObject.NULL)
            .put("km",e.odometerKm ?: JSONObject.NULL).put("elapsedMs",e.receivedElapsedMs)
            .put("quality",e.quality).put("contributedContinuousKm",change)
            .put("elapsedDeltaMs",if(older==null) JSONObject.NULL else
                CapturePolicy().elapsedDelta(older.receivedElapsedMs,e.receivedElapsedMs,older.bootId,e.bootId) ?: JSONObject.NULL)
            .put("crossingOdometerGaps",JSONArray(gaps.filter { g -> "ODOMETER" in g.sourceMask.split('|').map { it.trim() } &&
                older!=null && (g.lastDurableSeq==null || g.lastDurableSeq<e.seq) &&
                (g.firstAfterSeq==null || g.firstAfterSeq>older.seq) }.map { it.reason }))
    } }
    val snapshot=InputSnapshotBuilder().build(session,events,gaps,"review-device")
    val payload=snapshot.toString()
    val snapshotObject=dev.todaystrack.server.json.parseToJsonElement(payload).jsonObject
    val accepted=runCatching { validateSnapshot("submission-owner","review-device",payload) }
    val request=JSONObject(compositionRequest(snapshotObject))
    // Real SQLite, synthetic jobs only; no GenerationWorker, HTTP client, provider or audio.
    val states=JSONArray()
    val checks=JSONObject()
    JobStore("jdbc:sqlite::memory:").use { store ->
        val hash=sha256(payload.toByteArray())
        val id=store.adopt("submission-owner","review-intent",hash,payload,0)
        states.put(store.state(id))
        checks.put("duplicateIntentSameJob",store.adopt("submission-owner","review-intent",hash,payload,0)==id)
        checks.put("changedPayloadRejected",runCatching { store.adopt("submission-owner","review-intent","different",payload,0) }.isFailure)
        store.beginDispatch(id,request.toString());states.put(store.state(id))
        store.recover();states.put(store.state(id))
        checks.put("uncertainDispatchBlocked",!store.beginDispatch(id,request.toString()))
        checks.put("reservedMicros",store.reservedMicros())
    }
    val recovery=jsonArray(listOf("LOCAL_PENDING","ACCEPTANCE_UNCERTAIN","TRANSPORT_PENDING","RECONCILING","READY","AVAILABLE").map {
        JSONObject().put("state",it).put("mayCreateJob",GenerationRecoveryPolicy.mayCreateJob(it))
            .put("automaticallyResume",GenerationRecoveryPolicy.automaticallyResume(it)) })
    return JSONObject().put("id",c.getString("id")).put("observations",jsonArray(rows)).put("distanceSteps",jsonArray(distanceRows))
        .put("summary",snapshot.getJSONObject("summary")).put("snapshot",snapshot)
        .put("providerRequest",request).put("inputHash",sha256(payload.toByteArray()))
        .put("serverValidation",if(accepted.isSuccess) "ACCEPTED_LOCAL_VALIDATION" else accepted.exceptionOrNull().toString())
        .put("jobExperiment",JSONObject().put("states",states).put("checks",checks).put("boundary","SQLite 실제 실행 · 합성 작업 / 네트워크·음원·기기 실행 없음"))
        .put("vehicleRecoveryPolicy",recovery)
}
