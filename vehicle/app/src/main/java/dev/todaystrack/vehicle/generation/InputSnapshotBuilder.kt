package dev.todaystrack.vehicle.generation

import dev.todaystrack.vehicle.auth.DevelopmentIdentity
import dev.todaystrack.vehicle.journey.*
import dev.todaystrack.vehicle.storage.*
import org.json.JSONArray
import org.json.JSONObject

class InputSnapshotBuilder {
    fun build(session: CaptureSession, events: List<CaptureEvent>, gaps: List<CaptureGap>, deviceId: String): JSONObject {
        require(session.state == "SEALED")
        val facts = events.map { it.toSummaryFact() }
        require(facts.count { it.quality in setOf("FRESH", "VALID") } > 1) { "More observations needed" }
        val ownerId = "submission-owner"
        val first = events.minOf { it.seq }
        val end = events.maxOf { it.seq } + 1
        val coverageCanonical = "coverage-v1\n$ownerId\n$deviceId|${session.id}|$first|$end"
        val summary = JourneySummaryBuilder().build(facts,
            gaps.size, gaps.map { LocationGapBoundary(it.lastDurableSeq, it.firstAfterSeq, it.sourceMask) })
        val spans = JSONObject()
        summary.epochObservationSpansMs.forEach { (key, value) -> spans.put(key, value) }
        val summaryJson = JSONObject().put("positionSequence", JSONArray(summary.positionSequence))
            .put("positionEvidenceSeqs", JSONArray(summary.positionEvidenceSeqs)).put("continuousDistanceKm", summary.continuousDistanceKm)
            .put("gapCount", summary.gapCount).put("returnObserved", summary.returnObserved).put("epochObservationSpansMs", spans)
            .put("positionSegments", JSONArray(summary.positionSegments.map { segment -> JSONObject()
                .put("positionSequence", JSONArray(segment.positionSequence))
                .put("positionEvidenceSeqs", JSONArray(segment.positionEvidenceSeqs))
                .put("epochId", segment.epochId).put("bootId", segment.bootId) }))
            .put("positionUnknownBoundaries", JSONArray(summary.positionUnknownBoundaries.map { boundary -> JSONObject()
                .put("lastEvidenceSeq", boundary.lastEvidenceSeq ?: JSONObject.NULL)
                .put("firstEvidenceSeq", boundary.firstEvidenceSeq ?: JSONObject.NULL)
                .put("reasons", JSONArray(boundary.reasons))
                .put("lastDurableSeq", boundary.lastDurableSeq ?: JSONObject.NULL)
                .put("firstAfterSeq", boundary.firstAfterSeq ?: JSONObject.NULL) }))
            .put("dwellEvidence", JSONArray(summary.dwellEvidence.map { evidence -> JSONObject()
                .put("position", evidence.position).put("fromSeq", evidence.fromSeq).put("toSeq", evidence.toSeq)
                .put("observationCount", evidence.observationCount)
                .put("observedRepeatedPositionSpanMs", evidence.observedRepeatedPositionSpanMs)
                .put("epochId", evidence.epochId).put("bootId", evidence.bootId) }))
            .put("timeMeaning", "sum of same-known-boot monotonic observation spans per epoch; " +
                "NOT driving duration or guaranteed continuous sensor coverage; gaps not filled; " +
                "flat positions/return preserve observation order only; continuity is limited to each position segment; " +
                "repeated-position spans do not prove stops, visits, or dwell")
            .put("unknown", JSONArray(listOf("place names", "weather", "companions", "driver emotion", "events during gaps")))
        val rawCanonical = JSONArray(events.map { JSONObject().put("seq", it.seq).put("epoch", it.epochId).put("source", it.source)
            .put("utc", it.receivedAtUtc).put("elapsed", it.receivedElapsedMs).put("sourceTimestamp", it.sourceTimestampMs)
            .put("latitude", CanonicalSdkNumber.encode(it.latitude)).put("longitude", CanonicalSdkNumber.encode(it.longitude))
            .put("odometerKm", CanonicalSdkNumber.encode(it.odometerKm)).put("quality", it.quality) }).toString()
        return JSONObject().put("schemaVersion", 1).put("ownerId", ownerId).put("deviceId", deviceId)
            .put("sessionId", session.id).put("kind", session.kind).put("zoneId", session.zoneId)
            .put("sourceManifest", JSONArray().put(JSONObject().put("deviceId", deviceId).put("sessionId", session.id).put("fromSeq", first).put("toSeqExclusive", end)))
            .put("sourceCoverageHash", DevelopmentIdentity.sha256(coverageCanonical.toByteArray(Charsets.UTF_8)))
            .put("rawHash", DevelopmentIdentity.sha256(rawCanonical.toByteArray(Charsets.UTF_8)))
            .put("summaryVersion", "summary-v2").put("summary", summaryJson)
            .put("settings", JSONObject().put("genre", session.genre).put("lyricMode", session.lyricMode))
            .put("acquisition", "SDK").put("environment", "SIMULATED")
    }
}
