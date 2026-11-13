package dev.todaystrack.music

data class MediaConsent(val revision: Long, val selectedByUser: Boolean, val externalAnalysis: Boolean,
    val lyricsAllowed: Boolean = false, val directMusicImage: Boolean = false,
    val includeSpeech: Boolean = false, val revoked: Boolean = false)
data class MediaSelection(val kind: MediaKind, val sha256: String, val mimeType: String,
    val bytes: Long, val durationMs: Long? = null, val fromMs: Long? = null, val toMs: Long? = null,
    val consent: MediaConsent)
data class MediaLimits(val imageBytes: Long = 16L*1024*1024, val audioVideoBytes: Long = 64L*1024*1024,
    val maxDurationMs: Long = 10*60*1000, val maxImagePixels: Long = 20_000_000)

object MediaSelectionValidator {
    fun validate(selection: MediaSelection, limits: MediaLimits = MediaLimits()) {
        require(selection.kind!=MediaKind.MEMO) { "MEMO_IS_TEXT_NOT_UPLOAD" }
        require(selection.sha256.matches(Regex("[a-f0-9]{64}"))) { "INVALID_CONTENT_HASH" }
        require(selection.consent.revision>0 && selection.consent.selectedByUser) { "MANUAL_SELECTION_REQUIRED" }
        require(selection.consent.externalAnalysis && !selection.consent.revoked) { "ANALYSIS_CONSENT_REQUIRED" }
        val mimes=when(selection.kind) {
            MediaKind.PHOTO->setOf("image/jpeg","image/png")
            MediaKind.VIDEO->setOf("video/mp4")
            else->setOf("audio/wav","audio/mpeg","audio/mp4")
        }
        require(selection.mimeType in mimes) { "TRANSCODE_OR_SUPPORTED_FORMAT_REQUIRED" }
        require(selection.bytes in 1..if(selection.kind==MediaKind.PHOTO) limits.imageBytes else limits.audioVideoBytes) { "MEDIA_SIZE_LIMIT" }
        require(!selection.consent.directMusicImage || selection.kind==MediaKind.PHOTO) { "DIRECT_IMAGE_ONLY" }
        if(selection.kind==MediaKind.PHOTO) {
            require(selection.durationMs==null && selection.fromMs==null && selection.toMs==null) { "INVALID_IMAGE_RANGE" }
        } else {
            require(selection.durationMs!=null && selection.durationMs>0) { "DURATION_REQUIRED" }
            val start=selection.fromMs ?: 0;val end=selection.toMs ?: selection.durationMs
            require(start>=0 && end>start && end<=selection.durationMs && end-start<=limits.maxDurationMs) { "INVALID_SELECTED_RANGE" }
        }
    }
}
