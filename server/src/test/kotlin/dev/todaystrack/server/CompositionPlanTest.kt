package dev.todaystrack.server

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class CompositionPlanTest {
    private fun snapshot(returned: Boolean) = buildJsonObject {
        put("deviceId","private-device-reference");put("rawHash","private-raw-hash-reference")
        put("settings",buildJsonObject { put("genre","어쿠스틱");put("lyricMode","VOCAL") })
        put("summary",buildJsonObject {
            put("positionSequence",JsonArray(listOf("A","B","C","C","B","A").map(::JsonPrimitive)))
            put("continuousDistanceKm",8.0);put("gapCount",1);put("returnObserved",returned)
            put("timeMeaning","observation span, not driving duration")
        })
    }
    // Would catch dropping the actual SDK distance when producing the supplied factual lyrics.
    @Test fun suppliedLyricsUseObservedDistanceAndOnlySupportedReturn() {
        val request=json.parseToJsonElement(compositionRequest(snapshot(true))).jsonObject
        val prompt=request.string("input")
        assertTrue(prompt.contains("8 킬로미터"))
        assertTrue(prompt.contains("처음과 마지막 관측 위치가 같은 기록"))
        val unknownReturn=json.parseToJsonElement(compositionRequest(snapshot(false))).jsonObject.string("input")
        assertFalse(unknownReturn.contains("처음과 마지막 관측 위치가 같은 기록"))
    }
    // Would catch forwarding trace/device identifiers in the provider-facing music request.
    @Test fun providerProjectionExcludesPrivateTraceIdentifiers() {
        val request=compositionRequest(snapshot(true))
        assertFalse(request.contains("private-device-reference"))
        assertFalse(request.contains("private-raw-hash-reference"))
        assertFalse(request.contains("sourceManifest"))
    }

    @Test fun segmentAndRepeatedPositionProjectionDoesNotLeakEvidenceIdsOrAssertStops() {
        val original=snapshot(true)
        val summary=JsonObject(original.getValue("summary").jsonObject + mapOf(
            "positionSegments" to buildJsonArray { add(buildJsonObject {
                put("positionSequence",buildJsonArray { add("A");add("A") })
                put("positionEvidenceSeqs",buildJsonArray { add(1);add(3) })
                put("epochId","private-epoch");put("bootId","private-boot")
            }) },
            "dwellEvidence" to buildJsonArray { add(buildJsonObject {
                put("position","A");put("fromSeq",1);put("toSeq",3);put("observationCount",2)
                put("observedRepeatedPositionSpanMs",100);put("epochId","private-epoch");put("bootId","private-boot")
            }) }
        ))
        val prompt=json.parseToJsonElement(compositionRequest(JsonObject(original + ("summary" to summary)))).jsonObject.string("input")
        assertTrue(prompt.contains("observedRepeatedPositionSpanMs"))
        assertTrue(prompt.contains("not proof of a stop, visit or dwell"))
        assertTrue(prompt.contains("does not prove a continuous return across gaps"))
        assertFalse(prompt.contains("private-epoch"));assertFalse(prompt.contains("private-boot"))
        assertFalse(prompt.contains("positionEvidenceSeqs"));assertFalse(prompt.contains("fromSeq"))
        assertFalse(prompt.contains("soften only where repeated same-position observations support it"))
    }

    @Test fun legacyFlatSequenceWithoutSegmentsDoesNotClaimContinuityOrDwell() {
        val prompt=json.parseToJsonElement(compositionRequest(snapshot(true))).jsonObject.string("input")
        assertTrue(prompt.contains("continuity and repeated-position duration are unknown"))
        assertFalse(prompt.contains("\"positionSequence\""))
        assertFalse(prompt.contains("soften"))
    }
}
