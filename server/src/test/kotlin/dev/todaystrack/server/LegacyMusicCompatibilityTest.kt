package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import org.junit.Test
import kotlin.test.*

class LegacyMusicCompatibilityTest {
    @Test fun importingExistingSuccessfulCoveragePreservesFrozenPayloadHashLedgerAndPhoneEligibility() {
        val root=Files.createTempDirectory("legacy-music-compat").toFile();val owner="account";var snapshot="";var job=""
        val coverage=sha256("coverage-v1\n$owner\ncar|session|1|4".toByteArray())
        val payload=buildJsonObject {
            put("sourceCoverageHash",coverage);put("environment","SIMULATED")
            put("sourceManifest",buildJsonArray { add(buildJsonObject { put("deviceId","car");put("sessionId","session");put("fromSeq",1);put("toSeqExclusive",4) }) })
            put("settings",buildJsonObject { put("genre","어쿠스틱");put("lyricMode","VOCAL") })
            put("summary",buildJsonObject {
                put("positionSequence",buildJsonArray { add("A");add("B");add("A") });put("returnObserved",true);put("continuousDistanceKm",9)
                put("positionSegments",buildJsonArray { add(buildJsonObject { put("positionSequence",buildJsonArray { add("A");add("B");add("A") });put("positionEvidenceSeqs",buildJsonArray { add(1);add(2);add(3) }) }) })
            })
        }.toString()
        val hash=sha256(payload.toByteArray());var reserved=0L
        JobStore("jdbc:sqlite:${File(root,"server.db")}").use { old ->
            snapshot=old.snapshot(owner,"legacy-input",hash,payload);job=old.adopt(owner,"legacy-job",hash,payload,100_000)
            assertTrue(old.beginDispatch(job)) // Local ledger fixture only; no provider is instantiated.
            old.ready(job,"a".repeat(64),2048,90000);reserved=old.reservedMicros()
        }
        MusicService(root,ExecutionPolicy.testing(),ByteArray(32)).use { s ->
            val phone=MusicPrincipal(owner,"phone",DeviceRole.PHONE)
            val imported=s.importLegacy(phone,snapshot,"import")
            assertTrue(imported.getValue("input").jsonObject.getValue("features").jsonObject.getValue("metrics").jsonObject.isEmpty())
            val input=s.createInput(phone,buildJsonObject { put("intentId","phone-regen");put("operation","REGENERATE");put("parentInputId",imported.text("inputId"));put("preferences",preferencesJson(MusicPreferences(genre="FUNK")));put("materials",JsonArray(emptyList()));put("mediaIds",JsonArray(emptyList())) })
            assertEquals(coverage,input.text("sourceCoverageHash"));assertEquals(reserved,s.store.reservedMicros())
            assertFails { s.importLegacy(MusicPrincipal("other","phone",DeviceRole.PHONE),snapshot,"steal") }
        }
        JobStore("jdbc:sqlite:${File(root,"server.db")}").use { old ->
            assertEquals(payload,old.snapshotFor(owner,snapshot)!!.getValue("payload"));assertEquals(hash,old.snapshotFor(owner,snapshot)!!.getValue("hash"))
            assertEquals("READY",old.job(owner,job)!!.getValue("state"));assertEquals(reserved,old.reservedMicros())
        }
    }
}
