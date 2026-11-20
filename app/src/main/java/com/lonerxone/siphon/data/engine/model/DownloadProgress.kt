package com.lonerxone.siphon.data.engine.model

import java.io.File

data class DownloadProgress(
    val progressPercent: Float,     // 0.0 to 100.0
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long
) {
    val formattedSpeed: String
        get() {
            if (speedBps <= 0) return "--"
            val kb = speedBps / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB/s", mb)
            } else {
                String.format("%.0f KB/s", kb)
            }
        }

    val formattedEta: String
        get() {
            if (etaSeconds <= 0) return ""
            val m = etaSeconds / 60
            val s = etaSeconds % 60
            return if (m > 0) String.format("%dm %02ds left", m, s) else String.format("%ds left", s)
        }
}

data class DownloadRequest(
    val id: String,
    val url: String,
    val formatId: String? = null,
    val outputDir: File,
    val customFileName: String? = null,
    val speedLimitKbps: Long = 0L
)
