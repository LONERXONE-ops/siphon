package com.lonerxone.siphon.data.engine

import com.lonerxone.siphon.data.db.entity.EngineType
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEngineRouter @Inject constructor(
    private val ytDlpEngine: YtDlpEngine,
    private val aria2cEngine: Aria2cEngine
) {
    private val directExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz",
        "apk", "pdf", "iso", "exe", "msi", "bin",
        "dmg", "deb", "rpm", "epub", "cbz", "cbr"
    )

    fun selectEngine(url: String): DownloadEngine {
        val type = determineEngineType(url)
        return when (type) {
            EngineType.ARIA2C -> aria2cEngine
            EngineType.YT_DLP -> ytDlpEngine
        }
    }

    fun determineEngineType(url: String): EngineType {
        try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val ext = path.substringAfterLast('.', "").lowercase()

            if (ext in directExtensions) {
                return EngineType.ARIA2C
            }
        } catch (e: Exception) {
            // fallback
        }
        return EngineType.YT_DLP
    }
}
