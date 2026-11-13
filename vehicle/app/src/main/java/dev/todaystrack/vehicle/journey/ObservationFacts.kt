package dev.todaystrack.vehicle.journey

import dev.todaystrack.vehicle.capture.CapturePolicy
import dev.todaystrack.vehicle.storage.CaptureEvent

/** Projection for a new summary; stored events and already frozen snapshots are never rewritten. */
fun CaptureEvent.toSummaryFact(): Fact {
    val summaryQuality = if (source == "LOCATION" && quality == "FRESH") {
        val sourceTime = sourceTimestampMs
        when {
            sourceTime == null || sourceTime <= 0 -> "UNKNOWN_TIME"
            sourceTime > receivedAtUtc -> "FUTURE_TIME"
            receivedAtUtc - sourceTime > CapturePolicy.MAX_LOCATION_SOURCE_AGE_MS -> "STALE"
            else -> quality
        }
    } else quality
    return Fact(seq, epochId, bootId, receivedElapsedMs, source, summaryQuality,
        latitude, longitude, odometerKm, sourceError = kind == "SOURCE_ERROR")
}
