package dev.todaystrack.server

import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class SummaryValidationTest {
    private fun snapshot(span: Long = 100, boot: String = "boot", count: Int = 2): String {
        val summary=buildJsonObject {
            put("positionSequence",buildJsonArray { add("A");add("A") })
            put("positionEvidenceSeqs",buildJsonArray { add(1);add(3) })
            put("continuousDistanceKm",0);put("gapCount",0);put("returnObserved",true)
            put("epochObservationSpansMs",buildJsonObject { put("epoch",100) })
            put("timeMeaning","observation spans, not driving duration");put("unknown",buildJsonArray { add("weather") })
            put("positionSegments",buildJsonArray { add(buildJsonObject {
                put("positionSequence",buildJsonArray { add("A");add("A") })
                put("positionEvidenceSeqs",buildJsonArray { add(1);add(3) })
                put("epochId","epoch");put("bootId",boot)
            }) })
            put("positionUnknownBoundaries",buildJsonArray {})
            put("dwellEvidence",buildJsonArray { add(buildJsonObject {
                put("position","A");put("fromSeq",1);put("toSeq",3);put("observationCount",count)
                put("observedRepeatedPositionSpanMs",span);put("epochId","epoch");put("bootId",boot)
            }) })
        }
        return buildJsonObject {
            put("schemaVersion",1);put("ownerId","owner");put("deviceId","device");put("sessionId","session")
            put("kind","TRIP");put("zoneId","Asia/Seoul");put("rawHash","raw");put("summaryVersion","summary-v1")
            put("sourceManifest",buildJsonArray { add(buildJsonObject {
                put("deviceId","device");put("sessionId","session");put("fromSeq",1);put("toSeqExclusive",4)
            }) })
            put("sourceCoverageHash",sha256("coverage-v1\nowner\ndevice|session|1|4".toByteArray()))
            put("summary",summary);put("settings",buildJsonObject { put("genre","어쿠스틱");put("lyricMode","VOCAL") })
            put("acquisition","SDK");put("environment","SIMULATED")
        }.toString()
    }

    @Test fun acceptsAdditiveEvidenceAndExistingFrozenSummary() {
        validateSnapshot("owner","device",snapshot())
        val full=json.parseToJsonElement(snapshot()).jsonObject
        val oldSummary=JsonObject(full.getValue("summary").jsonObject - setOf("positionSegments","positionUnknownBoundaries","dwellEvidence"))
        validateSnapshot("owner","device",JsonObject(full + ("summary" to oldSummary)).toString())
        val original=full.getValue("summary").jsonObject
        val singletonUnknowns=buildJsonArray {
            for(seq in listOf(1,3)) add(buildJsonObject {
                put("positionSequence",buildJsonArray { add("A") })
                put("positionEvidenceSeqs",buildJsonArray { add(seq) })
                put("epochId","");put("bootId","unknown")
            })
        }
        val unknownSummary=JsonObject(original + mapOf("positionSegments" to singletonUnknowns,"dwellEvidence" to buildJsonArray {}))
        validateSnapshot("owner","device",JsonObject(full + ("summary" to unknownSummary)).toString())
    }

    @Test fun rejectsUnsupportedRepeatedPositionDuration() {
        assertFailsWith<IllegalArgumentException> { validateSnapshot("owner","device",snapshot(span=-1)) }
        assertFailsWith<IllegalArgumentException> { validateSnapshot("owner","device",snapshot(boot="unknown")) }
        assertFailsWith<IllegalArgumentException> { validateSnapshot("owner","device",snapshot(count=1)) }
    }
}
