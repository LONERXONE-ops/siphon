package com.lonerxone.siphon.data.engine.model

data class MediaFormat(
    val formatId: String,
    val label: String,             // e.g. "1080p MP4", "Audio only (M4A)"
    val ext: String,               // mp4, m4a, webm, etc.
    val fileSizeApprox: Long = 0L, // in bytes
    val isAudioOnly: Boolean = false,
    val resolution: String? = null
) {
    val formattedSize: String
        get() {
            if (fileSizeApprox <= 0) return "Unknown size"
            val mb = fileSizeApprox / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.0f MB", mb)
            }
        }
}

data class MediaMetadata(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L,
    val site: String = "",
    val formats: List<MediaFormat> = emptyList(),
    val isDirectFile: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return ""
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            return String.format("%d:%02d", m, s)
        }
}
