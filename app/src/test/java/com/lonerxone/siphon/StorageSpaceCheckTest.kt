package com.lonerxone.siphon

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageSpaceCheckTest {

    private fun checkSpace(usableSpace: Long, totalBytes: Long): Boolean {
        val requiredWithSafety = if (totalBytes > 0) totalBytes * 2 else 500 * 1024 * 1024L
        return usableSpace >= requiredWithSafety
    }

    @Test
    fun sufficientSpace_requires2xFileSize() {
        val fileSize = 200 * 1024 * 1024L // 200 MB

        // 350 MB free (< 2x 200 MB = 400 MB) -> FAILS
        assertThat(checkSpace(usableSpace = 350 * 1024 * 1024L, totalBytes = fileSize)).isFalse()

        // 450 MB free (>= 400 MB) -> SUCCEEDS
        assertThat(checkSpace(usableSpace = 450 * 1024 * 1024L, totalBytes = fileSize)).isTrue()
    }

    @Test
    fun unknownSize_requiresAtLeast500MB() {
        // Unknown file size (0L) requires at least 500MB safety threshold
        assertThat(checkSpace(usableSpace = 400 * 1024 * 1024L, totalBytes = 0L)).isFalse()
        assertThat(checkSpace(usableSpace = 600 * 1024 * 1024L, totalBytes = 0L)).isTrue()
    }
}
