package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.*

class ManualMediaTest {
    private val car=MusicPrincipal("owner","car",DeviceRole.CAR)
    private fun png()=ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4,3,BufferedImage.TYPE_INT_RGB),"png",it) }.toByteArray()
    @Test fun selectedPhotoIsDecodedStrippedEncryptedAndRevocationCannotResurrectIt() {
        val root=Files.createTempDirectory("manual-media").toFile();val bytes=png()
        MusicService(root,storageKey=ByteArray(32) { 7 }).use { s ->
            val sel=MediaSelection(MediaKind.PHOTO,contentHash(bytes),"image/png",bytes.size.toLong(),consent=MediaConsent(1,true,true,directMusicImage=true))
            val id=s.registerMedia(car,sel).text("mediaId")
            assertFails { s.uploadMedia(MusicPrincipal("other","x",DeviceRole.CAR),id,bytes) }
            assertFails { s.uploadMedia(car,id,bytes+1) }
            val uploaded=s.uploadMedia(car,id,bytes)
            assertEquals("UPLOADED",uploaded.text("state"))
            assertContentEquals(s.mediaBytes(car,id),s.mediaBytes(car,id))
            assertFalse(root.walkTopDown().filter { it.isFile && it.extension=="enc" }.single().readBytes().contentEquals(bytes))
            s.cleanupDerivative(car,id)
            assertFails { s.mediaBytes(car,id) }
            s.uploadMedia(car,id,bytes)
            assertTrue(s.mediaBytes(car,id).isNotEmpty())
            s.revokeMedia(car,id)
            assertFails { s.uploadMedia(car,id,bytes) }
            assertFalse(root.walkTopDown().any { it.extension=="enc" })
            assertEquals("REVOKED",s.mediaStatus(car,id).text("state"))
        }
    }
    @Test fun mislabeledAndCorruptImagesNeverBecomeUploaded() {
        val root=Files.createTempDirectory("manual-invalid").toFile()
        MusicService(root,storageKey=ByteArray(32)).use { s ->
            val bytes="not a photograph".toByteArray()
            val id=s.registerMedia(car,MediaSelection(MediaKind.PHOTO,contentHash(bytes),"image/jpeg",bytes.size.toLong(),consent=MediaConsent(1,true,true))).text("mediaId")
            assertFails { s.uploadMedia(car,id,bytes) }
            assertEquals("REGISTERED",s.mediaStatus(car,id).text("state"))
            assertFalse(root.walkTopDown().any { it.extension=="enc" })
        }
    }
    @Test fun everyAudioVideoKindIsRoutedThroughSelectedRangeProcessorAndConsentRevision() {
        val processor=MediaProcessor { bytes,s -> ProcessedMedia(bytes,"audio/wav",(s.toMs ?: s.durationMs!!)-(s.fromMs ?: 0)) }
        MusicService(Files.createTempDirectory("manual-kinds").toFile(),storageKey=ByteArray(32),mediaProcessor=processor).use { s ->
            for(kind in listOf(MediaKind.VOICE_MEMO,MediaKind.VIDEO,MediaKind.ENVIRONMENT_AUDIO,MediaKind.HUMMING)) {
                val bytes=byteArrayOf(1,2,3);val sel=MediaSelection(kind,contentHash(bytes),if(kind==MediaKind.VIDEO) "video/mp4" else "audio/wav",3,5000,1000,3000,MediaConsent(1,true,true))
                val id=s.registerMedia(car,sel).text("mediaId")
                assertEquals(2000,s.uploadMedia(car,id,bytes).getValue("durationMs").jsonPrimitive.long)
                s.revokeMedia(car,id)
            }
        }
    }
    @Test fun failedLocalDeletionIsReportedAsPendingRatherThanConfirmed() {
        val root=Files.createTempDirectory("cleanup-pending").toFile();val bytes=png()
        MusicService(root,storageKey=ByteArray(32)).use { s ->
            val id=s.registerMedia(car,MediaSelection(MediaKind.PHOTO,contentHash(bytes),"image/png",bytes.size.toLong(),consent=MediaConsent(1,true,true))).text("mediaId")
            s.uploadMedia(car,id,bytes)
            val path=java.io.File(root,"v2-media/$id.enc");assertTrue(path.delete());assertTrue(path.mkdir())
            val obstruction=java.io.File(path,"locked-marker").apply { writeText("synthetic deletion failure") }
            assertFails { s.cleanupDerivative(car,id) }
            assertTrue(s.mediaStatus(car,id).text("cleanupState").contains("PENDING"))
            obstruction.delete();path.delete()
        }
    }
}
