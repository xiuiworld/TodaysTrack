package dev.todaystrack.vehicle.journey

import org.junit.Assert.*
import org.junit.Test

class JourneySummaryBuilderTest {
    @Test fun odometerRegressionAndNegativeElapsedCannotContributeContinuousDistance() {
        for ((older, newer) in listOf(2000L to 1000L, -1L to 1000L)) {
            val result = JourneySummaryBuilder().build(listOf(
                Fact(1,"e","boot",older,"ODOMETER","VALID",km=100.0),
                Fact(2,"e","boot",newer,"ODOMETER","VALID",km=108.0),
                Fact(3,"e","boot",3000,"ODOMETER","VALID",km=109.0)), 0)
            // The invalid pair contributes nothing. A new forward pair may start at seq 2.
            assertEquals(1.0, result.continuousDistanceKm, 0.0)
        }
    }

    @Test fun explicitOdometerGapCannotBeCountedAsContinuousButLocationOnlyGapCan() {
        val events = listOf(
            Fact(1,"e","boot",100,"ODOMETER","VALID",km=100.0),
            Fact(2,"e","boot",200,"LOCATION","FRESH",lat=37.0,lon=127.0),
            Fact(3,"e","boot",300,"LOCATION","FRESH",lat=37.1,lon=127.1),
            Fact(4,"e","boot",400,"ODOMETER","VALID",km=108.0),
            Fact(5,"e","boot",500,"ODOMETER","VALID",km=109.0))
        val builder = JourneySummaryBuilder()
        assertEquals(1.0, builder.build(events,1,listOf(LocationGapBoundary(1,4,"ODOMETER"))).continuousDistanceKm,0.0)
        assertEquals(9.0, builder.build(events,1,listOf(LocationGapBoundary(1,4,"LOCATION"))).continuousDistanceKm,0.0)
        assertEquals(0.0, builder.build(events,1,listOf(LocationGapBoundary(1,null,"LOCATION|ODOMETER"))).continuousDistanceKm,0.0)
    }

    // Unknown identifiers cannot prove a shared odometer or elapsed-time domain.
    @Test fun blankAndCaseInsensitiveUnknownIdentitiesContributeNoDistanceOrTimeSpan() {
        for ((epoch, boot) in listOf("e" to "", "e" to "UNKNOWN", "" to "boot", "UNKNOWN" to "boot")) {
            val result = JourneySummaryBuilder().build(listOf(
                Fact(1, epoch, boot, 100, "ODOMETER", "VALID", km=10.0),
                Fact(2, epoch, boot, 300, "ODOMETER", "VALID", km=12.0)), 0)
            assertEquals("epoch=$epoch boot=$boot", 0.0, result.continuousDistanceKm, 0.0)
            assertTrue("epoch=$epoch boot=$boot", result.epochObservationSpansMs.isEmpty())
        }
    }

    // Grouping only by epoch would subtract elapsed values across boots or a clock regression.
    @Test fun epochSpansSumOnlyConsecutiveKnownBootMonotonicObservationRanges() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1, "e", "boot1", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e", "boot1", 300, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(3, "e", "boot2", 1000, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(4, "e", "boot2", 1100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(5, "e", "boot2", 50, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(6, "e", "boot2", 80, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(7, "e", "UNKNOWN", 1000, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(8, "e", "boot2", 9000, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(9, "e", "boot2", 9100, "LOCATION", "FRESH", lat=37.0, lon=127.0)), 0)
        assertEquals(mapOf("e" to 430L), result.epochObservationSpansMs)
    }

    // Joining the same position across a source gap would invent a repeated-position span.
    @Test fun samePositionAcrossLocationGapIsUnknownRatherThanDwellEvidence() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1, "e", "boot", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e", "boot", 150, "ODOMETER", "VALID", km=10.0),
            Fact(4, "e", "boot", 900, "LOCATION", "FRESH", lat=37.0, lon=127.0)), 1,
            listOf(LocationGapBoundary(2, 4, "LOCATION")))
        assertEquals(listOf(listOf("A"), listOf("A")), result.positionSegments.map { it.positionSequence })
        assertTrue(result.dwellEvidence.isEmpty())
        val gap = result.positionUnknownBoundaries.single()
        assertEquals(listOf("LOCATION_GAP"), gap.reasons)
        assertEquals(1L, gap.lastEvidenceSeq)
        assertEquals(4L, gap.firstEvidenceSeq)
        assertEquals(2L, gap.lastDurableSeq)
        assertEquals(4L, gap.firstAfterSeq)
    }

    // A boot/epoch change cannot make two identical observations a continuous span.
    @Test fun changedOrUnknownBootAndEpochSplitPositionEvidence() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1, "e1", "boot1", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e2", "boot1", 200, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(3, "e2", "boot2", 50, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(4, "e2", "unknown", 60, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(5, "e2", "unknown", 70, "LOCATION", "FRESH", lat=37.0, lon=127.0)), 0)
        assertEquals(5, result.positionSegments.size)
        assertTrue(result.dwellEvidence.isEmpty())
        assertTrue(result.positionUnknownBoundaries[0].reasons.contains("EPOCH_CHANGE"))
        assertTrue(result.positionUnknownBoundaries[1].reasons.contains("BOOT_CHANGE"))
        assertTrue(result.positionUnknownBoundaries.last().reasons.contains("UNKNOWN_BOOT"))
    }

    // Only uninterrupted repeated observations in one known clock domain supply a span.
    @Test fun repeatedFreshPositionsKeepObservedSpanAndIgnoreOdometerOnlyGap() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1, "e", "boot", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e", "boot", 150, "ODOMETER", "INVALID"),
            Fact(3, "e", "boot", 500, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(4, "e", "boot", 900, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(5, "e", "boot", 1000, "LOCATION", "FRESH", lat=37.1, lon=127.1)), 1,
            listOf(LocationGapBoundary(1, 5, "ODOMETER")))
        assertEquals(listOf("A", "A", "A", "B"), result.positionSegments.single().positionSequence)
        assertTrue(result.positionUnknownBoundaries.isEmpty())
        assertEquals(DwellEvidence("A", 1, 4, 3, 800, "e", "boot"), result.dwellEvidence.single())
    }

    // Source errors and rejected location samples must break inference even without gap rows.
    @Test fun rejectedLocationAndSourceErrorBreakRepeatedPositionEvidence() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1, "e", "boot", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e", "boot", 200, "LOCATION", "PRE_EPOCH", lat=37.0, lon=127.0),
            Fact(3, "e", "boot", 300, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(4, "e", "boot", 400, "LOCATION", "FRESH", lat=37.0, lon=127.0, sourceError=true),
            Fact(5, "e", "boot", 500, "LOCATION", "FRESH", lat=37.0, lon=127.0)), 0)
        assertEquals(listOf(1L, 3L, 5L), result.positionEvidenceSeqs)
        assertEquals(3, result.positionSegments.size)
        assertTrue(result.dwellEvidence.isEmpty())
        assertTrue(result.positionUnknownBoundaries.all { "INVALID_LOCATION" in it.reasons })
    }

    // Would fail if a gap is filled, stale coordinates are included, or driver emotion is invented.
    @Test fun observedOrderKeepsReturnAndUnknownGapWithoutInventingVisits() {
        val events = listOf(
            Fact(1, "e1", "boot", 100, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(2, "e1", "boot", 200, "ODOMETER", "VALID", km=100.0),
            Fact(3, "e1", "boot", 300, "LOCATION", "FRESH", lat=37.1, lon=127.1),
            Fact(4, "e1", "boot", 400, "ODOMETER", "VALID", km=102.0),
            Fact(5, "e1", "boot", 500, "LOCATION", "PRE_EPOCH", lat=38.0, lon=128.0),
            Fact(6, "e2", "boot", 900, "LOCATION", "FRESH", lat=37.0, lon=127.0),
            Fact(7, "e2", "boot", 950, "ODOMETER", "VALID", km=110.0),
            Fact(8, "e2", "boot", 1000, "ODOMETER", "VALID", km=111.0))
        val result = JourneySummaryBuilder().build(events, gapCount=2)
        assertEquals(listOf("A", "B", "A"), result.positionSequence)
        assertEquals(3.0, result.continuousDistanceKm, 0.0)
        assertEquals(2, result.gapCount)
        assertTrue(result.returnObserved)
        assertEquals(300L, result.epochObservationSpansMs["e1"])
        assertEquals(100L, result.epochObservationSpansMs["e2"])
        assertEquals(listOf(1L, 3L, 6L), result.positionEvidenceSeqs)
    }
    @Test fun resetAndUnknownBootDoNotInflateDistance() {
        val result = JourneySummaryBuilder().build(listOf(
            Fact(1,"e","boot",0,"ODOMETER","VALID",km=200.0),
            Fact(2,"e","boot",1,"ODOMETER","RESET",km=1.0),
            Fact(3,"e","boot",2,"ODOMETER","VALID",km=2.0),
            Fact(4,"f","unknown",3,"ODOMETER","VALID",km=10.0),
            Fact(5,"f","unknown",4,"ODOMETER","VALID",km=20.0)), 1)
        assertEquals(1.0, result.continuousDistanceKm, 0.0)
    }
    @Test fun overlappingRangesAreOnlyCoveredOnce() {
        assertEquals(listOf(Slice("d","s",1,10), Slice("d","t",1,2)), SourceCoverage.normalize(listOf(
            Slice("d","s",5,10), Slice("d","t",1,2), Slice("d","s",1,6))))
    }
}
