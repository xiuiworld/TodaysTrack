package dev.todaystrack.vehicle.integration

import dev.todaystrack.music.*
import dev.todaystrack.vehicle.generation.VehicleMusicProjection
import dev.todaystrack.vehicle.storage.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class VehicleMusicProjectionTest {
    private val session=CaptureSession("session","TRIP","Asia/Seoul","일렉트로닉","VOCAL",100000,"SEALED")
    private val epoch=CaptureEpoch("epoch","session","boot",0,100000,closed=true)
    private fun event(seq: Long,source: String,elapsed: Long,odo: Double?=null)=CaptureEvent(seq,"event-$seq","session","epoch","boot",source,source,
        100000+elapsed,elapsed,sourceTimestampMs=if(source=="LOCATION") 100000+elapsed else null,sourceTimeBasis=if(source=="LOCATION") "SDK_UTC_MILLIS" else null,
        latitude=if(source=="LOCATION") 37.0+seq/10000.0 else null,longitude=if(source=="LOCATION") 127.0 else null,
        sourceConfidence=98.0,odometerKm=odo,quality=if(source=="LOCATION") "FRESH" else "VALID")
    @Test fun actualRoomCaptureFieldsBecomePrivateFeatureInputWithIndependentSourceGaps() {
        val events=listOf(event(1,"LOCATION",0),event(2,"ODOMETER",100,100.0),event(3,"LOCATION",60000),event(4,"ODOMETER",60100,103.0))
        val gap=CaptureGap("gap","session","epoch","ODOMETER",2,4,"SOURCE_INTERRUPTED")
        val frozen=VehicleMusicProjection.freeze("intent",session,events,listOf(gap),listOf(epoch),"car")
        val body=musicJson.parseToJsonElement(frozen.payload).jsonObject.getValue("input").jsonObject
        val features=readFeatures(body.getValue("features").jsonObject)
        assertEquals(0.0,features.metrics.getValue("continuousDistanceKm").value,0.0)
        assertEquals(3.0,features.metrics.getValue("gapSpanningDistanceKm").value,0.0)
        assertFalse(features.metrics.keys.any { it.startsWith("speed") || it.contains("accuracy",true) })
        assertFalse(frozen.payload.contains("latitude"));assertFalse(frozen.payload.contains("127.0"))
        assertEquals("일렉트로닉",body.getValue("preferences").jsonObject.text("genre"))
    }
    @Test fun staleSourceIsRecheckedWithoutRewritingOriginalEventsAndOpenRecordsCannotFreeze() {
        val original=event(1,"LOCATION",60000).copy(sourceTimestampMs=100000)
        val events=listOf(original,event(2,"ODOMETER",60100,100.0),event(3,"ODOMETER",61100,101.0))
        val frozen=VehicleMusicProjection.freeze("intent",session,events,emptyList(),listOf(epoch),"car")
        val features=musicJson.parseToJsonElement(frozen.payload).jsonObject.getValue("input").jsonObject.getValue("features").jsonObject
        assertEquals("STALE",features.getValue("excluded").jsonObject.text("1"));assertEquals("FRESH",original.quality)
        try { VehicleMusicProjection.freeze("intent",session.copy(state="OPEN"),events,emptyList(),listOf(epoch),"car");fail("Open record must not freeze") } catch(expected: IllegalArgumentException) { }
    }
    @Test fun rawSdkErrorsAndUnknownQualityTextStayOnVehicleInsteadOfLeakingIntoMusicInput() {
        val raw="SDK exception latitude=37.123 longitude=127.456 secret-device-detail"
        val events=listOf(event(1,"LOCATION",0),event(2,"ODOMETER",100,100.0),event(3,"ODOMETER",60100,103.0),event(4,"LOCATION",60200).copy(quality=raw))
        val gap=CaptureGap("gap","session","epoch","LOCATION",1,4,raw)
        val frozen=VehicleMusicProjection.freeze("intent",session,events,listOf(gap),listOf(epoch),"car")
        assertFalse(frozen.payload.contains("37.123"));assertFalse(frozen.payload.contains("secret-device-detail"))
        assertEquals(raw,gap.reason);assertEquals(raw,events.last().quality)
        assertTrue(frozen.payload.contains("SOURCE_INTERRUPTION"));assertTrue(frozen.payload.contains("UNKNOWN_CAPTURE_QUALITY"))
    }
}
