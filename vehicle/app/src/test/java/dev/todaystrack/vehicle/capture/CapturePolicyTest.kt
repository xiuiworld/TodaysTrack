package dev.todaystrack.vehicle.capture

import org.junit.Assert.*
import org.junit.Test

class CapturePolicyTest {
    // Increasing source time alone must not turn a delayed SDK callback into a current observation.
    @Test fun delayedLocationInsideTheEpochIsNotFresh() {
        val p = CapturePolicy()
        assertEquals("STALE", p.locationQuality(3000, 1000, 30001, 2000))
        assertEquals("FRESH", p.locationQuality(3000, 1000, 6377, 2000))
        assertEquals("FRESH", p.locationQuality(3000, 1000, 13000, 2000))
        assertEquals("STALE", p.locationQuality(3000, 1000, 13001, 2000))
        assertEquals("FRESH", p.locationQuality(30002, 1000, 30003, 2000))
    }
    // STOP during asynchronous Room open must prevent its continuation from starting the SDK.
    @Test fun stopDuringOpenPreventsDelayedSdkStart() {
        val gate = CaptureStartGate()
        assertTrue(gate.tryStart())
        assertTrue(gate.stop())
        var sdkStarts = 0
        gate.runIfActive { sdkStarts++ }
        assertEquals(0, sdkStarts)
        assertFalse(gate.tryStart())
    }
    // A callback queued before close must be checked again when it reaches the main thread.
    @Test fun lateCallbackAfterStopCannotResumeCapture() {
        val gate = CaptureStartGate()
        assertTrue(gate.tryStart())
        var deliveries = 0
        val queuedCallback = { gate.runIfActive { deliveries++ } }
        queuedCallback()
        gate.stop()
        queuedCallback()
        assertEquals(1, deliveries)
        assertFalse(gate.stop())
    }
    // A cached/older location must not become a newly observed point.
    @Test fun oldOrRepeatedSourceTimeIsPreservedButExcluded() {
        val p = CapturePolicy()
        assertEquals("PRE_EPOCH", p.locationQuality(800, 1000, 1200, null))
        assertEquals("DUPLICATE", p.locationQuality(1100, 1000, 1300, 1100))
        assertEquals("OUT_OF_ORDER", p.locationQuality(1050, 1000, 1300, 1100))
        assertEquals("FRESH", p.locationQuality(1250, 1000, 1300, 1100))
        assertEquals("UNKNOWN_TIME", p.locationQuality(null, 1000, 1300, null))
    }
    // Different boot/epoch or a reset must never add a fake traveled distance.
    @Test fun distanceRunDoesNotBridgeResetBootOrEpoch() {
        val p = CapturePolicy()
        assertEquals(2.0, p.distanceDelta(100.0, 102.0, true, true)!!, 0.0)
        assertNull(p.distanceDelta(102.0, 3.0, true, true))
        assertNull(p.distanceDelta(100.0, 102.0, false, true))
        assertNull(p.distanceDelta(100.0, 102.0, true, false))
        assertNull(p.distanceDelta(null, 102.0, true, true))
        assertNull(p.distanceDelta(100.0, Double.NaN, true, true))
    }
    @Test fun elapsedTimeOnlyUsesOneKnownBoot() {
        assertEquals(600L, CapturePolicy().elapsedDelta(100, 700, "a", "a"))
        assertNull(CapturePolicy().elapsedDelta(100, 700, "a", "b"))
        assertNull(CapturePolicy().elapsedDelta(100, 700, "unknown", "unknown"))
    }
}
