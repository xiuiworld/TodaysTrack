package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assume.assumeTrue
import kotlin.test.*

class MediaDecoderTest {
    private fun wav(seconds: Int): ByteArray {
        val samples=16000*seconds;val b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36+samples*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(16000);b.putInt(32000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(samples*2)
        repeat(samples) { b.putShort((kotlin.math.sin(it*2*Math.PI*440/16000)*4000).toInt().toShort()) };return b.array()
    }
    @Test fun realAudioDecoderValidatesAndUsesOnlySelectedRangeForAllManualAudioKinds() {
        val executable=System.getenv("TODAYSTRACK_FFMPEG");assumeTrue("Local decoder not supplied",executable!=null)
        val source=wav(6);val processor=SafeMediaProcessor { executable!! }
        for(kind in listOf(MediaKind.VOICE_MEMO,MediaKind.ENVIRONMENT_AUDIO,MediaKind.HUMMING)) {
            val selection=MediaSelection(kind,contentHash(source),"audio/wav",source.size.toLong(),6000,1000,3000,MediaConsent(1,true,true))
            val result=processor.process(source,selection)
            assertEquals("audio/mpeg",result.mime);assertTrue(result.durationMs!! in 1900..3000)
            assertTrue(result.bytes.size<source.size)
            assertFails { processor.process(source.copyOf(30),selection) }
            assertFails { processor.process(source,selection.copy(fromMs=10000,toMs=12000,durationMs=12000)) }
        }
    }
    @Test fun realVideoDecoderStripsAudioUnlessSpeechWasExplicitlyPermitted() {
        val executable=System.getenv("TODAYSTRACK_FFMPEG");assumeTrue("Local decoder not supplied",executable!=null)
        val temp=Files.createTempDirectory("selected-video-test").toFile();val source=File(temp,"input.mp4");val derivative=File(temp,"derived.mp4")
        try {
            val generated=ProcessBuilder(executable!!,"-nostdin","-v","error","-f","lavfi","-i","color=c=blue:s=64x64:r=4:d=4","-f","lavfi","-i","sine=frequency=440:duration=4","-c:v","libx264","-c:a","aac","-shortest","-y",source.absolutePath).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(generated.waitFor(20,TimeUnit.SECONDS));assertEquals(0,generated.exitValue())
            val bytes=source.readBytes();val selection=MediaSelection(MediaKind.VIDEO,contentHash(bytes),"video/mp4",bytes.size.toLong(),4000,1000,3000,MediaConsent(1,true,true,includeSpeech=false))
            val result=SafeMediaProcessor { executable }.process(bytes,selection);derivative.writeBytes(result.bytes)
            assertEquals("video/mp4",result.mime);assertTrue(result.durationMs!! in 1000..3000)
            val audio=ProcessBuilder(executable,"-nostdin","-v","error","-i",derivative.absolutePath,"-map","0:a:0","-f","null","-").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            assertTrue(audio.waitFor(20,TimeUnit.SECONDS));assertNotEquals(0,audio.exitValue())
        } finally { source.delete();derivative.delete();temp.delete() }
    }
}
