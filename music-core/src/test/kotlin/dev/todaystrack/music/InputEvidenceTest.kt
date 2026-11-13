package dev.todaystrack.music

import org.junit.Test
import kotlin.test.*

class InputEvidenceTest {
    private fun loc(seq: Long,time: Long=100000+seq)=Observation(seq,Source.LOCATION,"e","b",seq*100,100000+seq,"FRESH",time,"SDK_UTC_MILLIS",37.0,127.0+seq/10000.0)
    @Test fun duplicateAndOutOfOrderSourceTimesCannotClaimFreshContinuity() {
        val f=FeatureExtractor().extract(ObservationBatch(listOf(loc(1),loc(2,100001),loc(3,100000),loc(4))))
        assertEquals("DUPLICATE_SOURCE_TIME",f.excluded[2]);assertEquals("OUT_OF_ORDER_SOURCE_TIME",f.excluded[3])
        assertFalse(f.metrics.containsKey("startEndDistanceMeters"))
    }
    @Test fun observationBeforeTheRequestStartedIsNotPairedWithItsResponse() {
        val f=FeatureExtractor().extract(ObservationBatch(listOf(loc(1).copy(requestStartedElapsedMs=101))))
        assertEquals("INVALID_REQUEST_TIME",f.excluded[1])
        val priorEpoch=loc(1).copy(receivedAtUtcMs=100010,epochStartedUtcMs=100005)
        assertEquals("PRE_EPOCH",FeatureExtractor().extract(ObservationBatch(listOf(priorEpoch))).excluded[1])
    }
    @Test fun unsupportedOptionalSourcesAndPermissionDenialsDoNotBecomeDrivingClaims() {
        val obs=listOf(Observation(1,Source.SPEED,"e","b",1,1,"VALID",value=40.0,unit="m/s"),Observation(2,Source.WIPER,"e","b",2,2,"VALID",state="ON"))
        val f=FeatureExtractor().extract(ObservationBatch(obs,statuses=listOf(SourceStatus(Source.SPEED,Availability.UNSUPPORTED),SourceStatus(Source.WIPER,Availability.PERMISSION_DENIED))))
        assertEquals(setOf("UNSUPPORTED","PERMISSION_DENIED"),f.excluded.values.toSet());assertTrue(f.stateRuns.isEmpty());assertFalse(f.metrics.containsKey("speedMeanMps"))
    }
    @Test fun headingMeanUsesCircularRatherThanArithmeticAngles() {
        val f=FeatureExtractor().extract(ObservationBatch(listOf(Observation(1,Source.HEADING,"e","b",1,1,"VALID",value=359.0,unit="degrees"),Observation(2,Source.HEADING,"e","b",2,2,"VALID",value=1.0,unit="degrees")),statuses=listOf(SourceStatus(Source.HEADING,Availability.AVAILABLE,true))))
        val mean=f.metrics.getValue("headingMean").value
        assertTrue(mean<0.001 || mean>359.999);assertEquals(2.0,f.metrics.getValue("headingObservedChange").value)
    }
}
