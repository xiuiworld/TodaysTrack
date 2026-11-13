package dev.todaystrack.server

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class GenerationRecoveryTest {
    private val audio=ByteArray(2048) { (it % 127).toByte() }

    private fun interrupted(root: File): String = JobStore("jdbc:sqlite:${File(root,"jobs.db")}").use { db ->
        db.adopt("owner","intent","fixed-hash","{}",100_000).also {
            assertTrue(db.beginDispatch(it,"saved-request"))
            db.updateState(it,"VALIDATING")
        }
    }

    // A restart after the atomic move must finish the saved result without a provider request.
    @Test fun restartResumesSavedFinalAssetWithoutAnotherAttempt() {
        val root=Files.createTempDirectory("generation-recovery").toFile()
        val id=interrupted(root)
        val final=File(root,"assets/$id.mp3").apply { parentFile.mkdirs();writeBytes(audio) }
        JobStore("jdbc:sqlite:${File(root,"jobs.db")}").use { db ->
            db.recover()
            val worker=GenerationWorker(root,db) { file,_ -> assertContentEquals(audio,file.readBytes());90_000L }
            assertTrue(worker.processNext())
            assertEquals("READY",db.state(id))
            assertEquals(sha256(audio),db.job("owner",id)?.get("asset_hash"))
            assertEquals("LIVE",db.job("owner",id)?.get("mode"))
            assertEquals(100_000,db.reservedMicros())
            assertFalse(db.beginDispatch(id))
            assertContentEquals(audio,final.readBytes())
        }
    }

    @Test fun restartResumesPartialAssetButHoldsMissingResult() {
        val root=Files.createTempDirectory("generation-part-recovery").toFile()
        val id=interrupted(root)
        File(root,"assets/$id.mp3.part").apply { parentFile.mkdirs();writeBytes(audio) }
        JobStore("jdbc:sqlite:${File(root,"jobs.db")}").use { db ->
            db.recover()
            assertTrue(GenerationWorker(root,db) { file,_ -> assertContentEquals(audio,file.readBytes());90_000L }.processNext())
            assertEquals("READY",db.state(id))
            assertTrue(File(root,"assets/$id.mp3").isFile)
        }
        val missingRoot=Files.createTempDirectory("generation-missing-recovery").toFile()
        val missingId=interrupted(missingRoot)
        JobStore("jdbc:sqlite:${File(missingRoot,"jobs.db")}").use { db ->
            db.recover()
            assertTrue(GenerationWorker(missingRoot,db) { _,_ -> fail("No saved audio to validate") }.processNext())
            assertEquals("RECONCILING",db.state(missingId))
            assertEquals(100_000,db.reservedMicros())
            assertFalse(db.beginDispatch(missingId))
        }
    }

    @Test fun traceWriteFailureDoesNotRevokeReadyAsset() {
        val root=Files.createTempDirectory("generation-trace-recovery").toFile()
        val id=interrupted(root)
        File(root,"assets/$id.mp3.part").apply { parentFile.mkdirs();writeBytes(audio) }
        // A real filesystem write failure after the durable READY commit.
        File(root,"generation/$id/trace.json").mkdirs()
        JobStore("jdbc:sqlite:${File(root,"jobs.db")}").use { db ->
            assertTrue(GenerationWorker(root,db) { _,_ -> 90_000L }.processNext())
            assertEquals("READY",db.state(id))
            assertTrue(File(root,"assets/$id.mp3").isFile)
        }
    }

    @Test fun restartCanExtractOnlyTheAlreadySavedProviderResponse() {
        val root=Files.createTempDirectory("generation-response-recovery").toFile()
        val id=interrupted(root)
        val encoded=java.util.Base64.getEncoder().encodeToString(audio)
        File(root,"generation/$id/response.json").apply {
            parentFile.mkdirs()
            writeText("""{"steps":[{"type":"model_output","content":[{"type":"audio","mime_type":"audio/mpeg","data":"$encoded"}]}]}""")
        }
        JobStore("jdbc:sqlite:${File(root,"jobs.db")}").use { db ->
            db.recover()
            assertTrue(GenerationWorker(root,db) { file,_ -> assertContentEquals(audio,file.readBytes());90_000L }.processNext())
            assertEquals("READY",db.state(id))
            assertEquals(100_000,db.reservedMicros())
            assertFalse(db.beginDispatch(id))
        }
    }
}
