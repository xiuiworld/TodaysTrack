package dev.todaystrack.vehicle.journey

import dev.todaystrack.vehicle.storage.CaptureEvent
import org.junit.Assert.*
import org.junit.Test

class ObservationFactsTest {
    // capture-v1 FRESH labels must not admit delayed coordinates into a newly frozen summary.
    @Test fun legacyFreshDelayedPositionIsExcludedWithoutRewritingStoredEvents() {
        val events = listOf(
            observation(1, 29998),
            observation(2, 10000).copy(latitude=38.0, longitude=128.0),
            observation(3, 29999))
        val originals = events.map { it.copy() }
        val facts = events.map { it.toSummaryFact() }
        val summary = JourneySummaryBuilder().build(facts, 0)
        assertEquals("STALE", facts[1].quality)
        assertEquals(listOf(1L, 3L), summary.positionEvidenceSeqs)
        assertEquals(listOf(listOf("A"), listOf("A")), summary.positionSegments.map { it.positionSequence })
        assertTrue(summary.dwellEvidence.isEmpty())
        assertEquals(originals, events)
        assertEquals("FRESH", events[1].quality)
        assertEquals("capture-v1", events[1].policyVersion)
    }

    // Unknown/future source times cannot remain fresh merely because an old policy labeled them so.
    @Test fun legacyFreshMissingInvalidOrFutureTimesAreDowngraded() {
        for (sourceTime in listOf(null, 0L, -1L)) {
            assertEquals("UNKNOWN_TIME", observation(1, sourceTime).toSummaryFact().quality)
        }
        assertEquals("FUTURE_TIME", observation(1, 30001).toSummaryFact().quality)
        assertEquals("FRESH", observation(1, 20000).toSummaryFact().quality)
        assertEquals("STALE", observation(1, 19999).toSummaryFact().quality)
    }

    // Fresh finite inputs retain their values; rejected facts are never promoted by projection.
    @Test fun validValuesExistingNonFreshQualityAndSourceErrorsArePreserved() {
        val event = observation(7, 29999).copy(odometerKm=20010.0)
        assertEquals(Fact(7, "epoch", "boot", 700, "LOCATION", "FRESH", 37.0, 127.0, 20010.0),
            event.toSummaryFact())
        for (quality in listOf("STALE", "PRE_EPOCH", "INVALID", "OUT_OF_ORDER", "UNKNOWN_TIME")) {
            assertEquals(quality, event.copy(quality=quality).toSummaryFact().quality)
        }
        assertEquals("VALID", event.copy(source="ODOMETER", quality="VALID", sourceTimestampMs=null).toSummaryFact().quality)
        assertTrue(event.copy(kind="SOURCE_ERROR").toSummaryFact().sourceError)
    }

    private fun observation(seq: Long, sourceTime: Long?): CaptureEvent = CaptureEvent(
        seq=seq, eventId="event-$seq", sessionId="session", epochId="epoch", bootId="boot",
        kind="LOCATION", source="LOCATION", receivedAtUtc=30000, receivedElapsedMs=seq * 100,
        sourceTimestampMs=sourceTime, sourceTimeBasis="UTC", latitude=37.0, longitude=127.0,
        quality="FRESH", policyVersion="capture-v1")
}
