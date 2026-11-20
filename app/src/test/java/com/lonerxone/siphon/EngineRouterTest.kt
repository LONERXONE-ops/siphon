package com.lonerxone.siphon

import com.google.common.truth.Truth.assertThat
import com.lonerxone.siphon.data.db.entity.EngineType
import org.junit.Test
import java.net.URI

class EngineRouterTest {

    private val directExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz",
        "apk", "pdf", "iso", "exe", "msi", "bin",
        "dmg", "deb", "rpm", "epub", "cbz", "cbr"
    )

    private fun determineEngine(url: String): EngineType {
        try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in directExtensions) {
                return EngineType.ARIA2C
            }
        } catch (e: Exception) {}
        return EngineType.YT_DLP
    }

    @Test
    fun directArchiveAndAppUrls_routeToAria2c() {
        assertThat(determineEngine("https://example.com/files/archive.zip")).isEqualTo(EngineType.ARIA2C)
        assertThat(determineEngine("https://mirror.org/downloads/app-release.apk")).isEqualTo(EngineType.ARIA2C)
        assertThat(determineEngine("https://cdn.site.com/docs/manual.pdf")).isEqualTo(EngineType.ARIA2C)
        assertThat(determineEngine("https://releases.ubuntu.com/noble/ubuntu-24.04-desktop-amd64.iso")).isEqualTo(EngineType.ARIA2C)
    }

    @Test
    fun mediaSiteUrls_routeToYtDlp() {
        assertThat(determineEngine("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isEqualTo(EngineType.YT_DLP)
        assertThat(determineEngine("https://vimeo.com/76979871")).isEqualTo(EngineType.YT_DLP)
        assertThat(determineEngine("https://twitter.com/user/status/123456789")).isEqualTo(EngineType.YT_DLP)
        assertThat(determineEngine("https://www.tiktok.com/@user/video/987654321")).isEqualTo(EngineType.YT_DLP)
    }
}
