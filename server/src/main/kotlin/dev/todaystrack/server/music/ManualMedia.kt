package dev.todaystrack.server.music

import dev.todaystrack.music.*
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO

data class ProcessedMedia(val bytes: ByteArray,val mime: String,val durationMs: Long?)
fun interface MediaProcessor { fun process(bytes: ByteArray,selection: MediaSelection): ProcessedMedia }
class SafeMediaProcessor(private val scratchRoot: File?=null,private val ffmpeg: ()->String={ System.getenv("TODAYSTRACK_FFMPEG") ?: error("MEDIA_DECODER_UNAVAILABLE") }): MediaProcessor {
    init { scratchRoot?.let { require(it.isDirectory || it.mkdirs()) } }
    fun cleanAbandonedScratch(): Boolean {
        val root=scratchRoot?.canonicalFile ?: return true
        var complete=true
        for(dir in root.listFiles().orEmpty()) {
            if(!dir.name.matches(Regex("media-[A-Za-z0-9-]+")) || Files.isSymbolicLink(dir.toPath()) || !dir.canonicalFile.toPath().startsWith(root.toPath())) { complete=false;continue }
            for(file in dir.listFiles().orEmpty()) {
                if(file.name in setOf("source.bin","selected.mp3","selected.mp4","progress.txt") && !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile==dir.canonicalFile) {
                    if(!file.delete()) complete=false
                } else complete=false
            }
            if(!dir.delete()) complete=false
        }
        return complete
    }
    override fun process(bytes: ByteArray,selection: MediaSelection): ProcessedMedia {
        if(selection.kind==MediaKind.PHOTO) {
            val expected=if(selection.mimeType=="image/png") "png" else "jpeg"
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val readers=ImageIO.getImageReaders(input);require(readers.hasNext()) { "CORRUPT_IMAGE" }
                val reader=readers.next()
                try {
                    require(reader.formatName.equals(expected,true) || (expected=="jpeg" && reader.formatName.equals("jpg",true))) { "MIME_CONTENT_MISMATCH" }
                    reader.input=input
                    val w=reader.getWidth(0);val h=reader.getHeight(0)
                    require(w>0 && h>0 && w.toLong()*h<=MediaLimits().maxImagePixels) { "IMAGE_PIXEL_LIMIT" }
                    var image=reader.read(0) ?: error("CORRUPT_IMAGE")
                    if(maxOf(w,h)>2048) {
                        val scale=2048.0/maxOf(w,h)
                        val resized=java.awt.image.BufferedImage((w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1),if(expected=="png") java.awt.image.BufferedImage.TYPE_INT_ARGB else java.awt.image.BufferedImage.TYPE_INT_RGB)
                        val graphics=resized.createGraphics()
                        try { graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(image,0,0,resized.width,resized.height,null) } finally { graphics.dispose() }
                        image=resized
                    }
                    val out=ByteArrayOutputStream();check(ImageIO.write(image,expected,out))
                    require(out.size()<=MediaLimits().imageBytes)
                    return ProcessedMedia(out.toByteArray(),selection.mimeType,null) // No EXIF/GPS copied.
                } finally { reader.dispose() }
            }
        }
        require(signatureMatches(bytes,selection.mimeType)) { "MIME_CONTENT_MISMATCH" }
        val scratch=if(scratchRoot!=null) Files.createTempDirectory(scratchRoot.toPath(),"media-").toFile() else Files.createTempDirectory("todaystrack-media-").toFile()
        restrictScratch(scratch)
        val source=File(scratch,"source.bin");val output=File(scratch,if(selection.kind==MediaKind.VIDEO) "selected.mp4" else "selected.mp3")
        val progress=File(scratch,"progress.txt")
        try {
            source.writeBytes(bytes)
            val end=selection.toMs ?: selection.durationMs!!;val start=selection.fromMs ?: 0
            val duration=end-start
            val args=mutableListOf(ffmpeg(),"-nostdin","-v","error","-xerror","-protocol_whitelist","file,pipe","-i",source.absolutePath,
                "-ss",(start/1000.0).toString(),"-t",(duration/1000.0).toString(),"-map_metadata","-1","-map_chapters","-1")
            if(selection.kind==MediaKind.VIDEO) {
                args.addAll(listOf("-map","0:v:0","-vf","scale=640:360:force_original_aspect_ratio=decrease","-r","2","-c:v","libx264","-preset","fast","-crf","28"))
                if(selection.consent.includeSpeech) args.addAll(listOf("-map","0:a?","-c:a","aac","-b:a","64k")) else args.add("-an")
                args.addAll(listOf("-movflags","+faststart"))
            } else args.addAll(listOf("-map","0:a:0","-vn","-ar","24000","-ac","1","-c:a","libmp3lame","-b:a","96k"))
            args.addAll(listOf("-fs","64000000","-progress","pipe:1","-nostats","-y",output.absolutePath))
            val process=ProcessBuilder(args).redirectOutput(progress).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if(!process.waitFor(60,TimeUnit.SECONDS)) { process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);error("MEDIA_DECODE_TIMEOUT") }
            require(process.exitValue()==0 && output.isFile && output.length() in 1..63_999_999) { "CORRUPT_OR_OVERSIZED_MEDIA" }
            require(progress.length()<1_000_000) { "MEDIA_PROGRESS_LIMIT" }
            val actual=progress.readLines().filter { it.startsWith("out_time_us=") }.mapNotNull { it.substringAfter('=').toLongOrNull() }.maxOrNull()?.div(1000) ?: error("MEDIA_DURATION_UNAVAILABLE")
            require(actual>0 && kotlin.math.abs(actual-duration)<=1000) { "SELECTED_RANGE_OUTSIDE_ACTUAL_MEDIA" }
            return ProcessedMedia(output.readBytes(),if(selection.kind==MediaKind.VIDEO) "video/mp4" else "audio/mpeg",actual)
        } finally {
            listOf(source,output,progress).forEach { it.delete() };scratch.delete()
        }
    }
    private fun signatureMatches(b: ByteArray,mime: String): Boolean {
        fun at(offset: Int,text: String)=b.size>=offset+text.length && b.copyOfRange(offset,offset+text.length).toString(Charsets.US_ASCII)==text
        return when(mime) {
            "audio/wav" -> at(0,"RIFF") && at(8,"WAVE")
            "audio/mpeg" -> at(0,"ID3") || (b.size>=2 && b[0].toInt() and 255==255 && b[1].toInt() and 224==224)
            "audio/mp4","video/mp4" -> at(4,"ftyp")
            else -> false
        }
    }
}

private fun restrictScratch(directory: File) {
    val path=directory.toPath()
    val acl=Files.getFileAttributeView(path,java.nio.file.attribute.AclFileAttributeView::class.java)
    if(acl!=null) {
        val entry=java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW)
            .setPrincipal(Files.getOwner(path)).setPermissions(java.nio.file.attribute.AclEntryPermission.entries.toSet())
            .setFlags(java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT,java.nio.file.attribute.AclEntryFlag.FILE_INHERIT).build()
        acl.acl=listOf(entry)
    } else Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
}

/** Only sanitized selected derivatives are durable here, authenticated-encrypted per media ID. */
class MediaVault(private val directory: File,key: ByteArray) {
    private val secret=SecretKeySpec(key.copyOf().also { require(it.size==32) },"AES")
    init { require(directory.isDirectory || directory.mkdirs()) }
    private fun file(id: String)=File(directory,"${UUID.fromString(id)}.enc")
    fun exists(id: String)=file(id).isFile
    fun put(id: String,bytes: ByteArray) {
        val nonce=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,secret,GCMParameterSpec(128,nonce));updateAAD(id.toByteArray()) }
        val target=file(id);val part=File(directory,"$id.part")
        try { durableWrite(part,nonce+cipher.doFinal(bytes));Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        finally { part.delete() }
    }
    fun get(id: String): ByteArray {
        val f=file(id);require(f.length() in 28..64L*1024*1024+28)
        val data=f.readBytes();val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,data.copyOfRange(0,12)));updateAAD(id.toByteArray()) }
        return cipher.doFinal(data.copyOfRange(12,data.size))
    }
    fun delete(id: String) { val f=file(id);check(!f.exists() || f.delete()) { "MEDIA_CLEANUP_PENDING" } }
}

internal fun durableWrite(file: File,bytes: ByteArray) {
    java.io.FileOutputStream(file).use { it.write(bytes);it.fd.sync() }
}
