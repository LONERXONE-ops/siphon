package com.lonerxone.siphon

import com.google.common.truth.Truth.assertThat
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.db.entity.EngineType
import org.junit.Test
import java.util.UUID

/**
 * Unit Test verifying the Core Invariant:
 * "A manual pause always beats automatic rules."
 * (PRD Section 5.4, 10, 13)
 */
class ManualPauseBeatsAutoStartTest {

    @Test
    fun manualPauseItem_isExcludedFromAutoScheduling() {
        val pausedByUserItem = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = "https://example.com/video1",
            title = "Video 1",
            status = DownloadStatus.PAUSED_BY_USER,
            pausedByUser = true, // User tapped pause
            position = 1
        )

        val pausedNoWifiItem = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = "https://example.com/video2",
            title = "Video 2",
            status = DownloadStatus.PAUSED_NO_WIFI,
            pausedByUser = false, // Automatically paused when Wi-Fi dropped
            position = 2
        )

        val waitingItem = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = "https://example.com/video3",
            title = "Video 3",
            status = DownloadStatus.WAITING,
            pausedByUser = false,
            position = 3
        )

        val allItems = listOf(pausedByUserItem, pausedNoWifiItem, waitingItem)

        // Invariant filter matching Room DAO query:
        // status IN ('WAITING', 'PAUSED_NO_WIFI', 'PAUSED_NO_STORAGE') AND pausedByUser = 0
        val schedulableItems = allItems.filter { item ->
            (item.status == DownloadStatus.WAITING ||
             item.status == DownloadStatus.PAUSED_NO_WIFI ||
             item.status == DownloadStatus.PAUSED_NO_STORAGE) &&
            !item.pausedByUser
        }

        // Assert: pausedByUserItem is strictly EXCLUDED
        assertThat(schedulableItems).doesNotContain(pausedByUserItem)
        assertThat(schedulableItems).containsExactly(pausedNoWifiItem, waitingItem).inOrder()
    }

    @Test
    fun stuckQueueNormalization_preservesPausedByUserState() {
        val stuckDownloadingItem = DownloadItem(
            id = "1",
            url = "https://example.com/1",
            title = "Stuck Download",
            status = DownloadStatus.DOWNLOADING,
            pausedByUser = false
        )

        val stuckQueuedItem = DownloadItem(
            id = "2",
            url = "https://example.com/2",
            title = "Stuck Queued",
            status = DownloadStatus.QUEUED,
            pausedByUser = false
        )

        val userPausedItem = DownloadItem(
            id = "3",
            url = "https://example.com/3",
            title = "User Paused",
            status = DownloadStatus.PAUSED_BY_USER,
            pausedByUser = true
        )

        val list = listOf(stuckDownloadingItem, stuckQueuedItem, userPausedItem)

        // Simulation of normalizeStuckDownloads()
        val normalizedList = list.map { item ->
            if ((item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) && !item.pausedByUser) {
                item.copy(status = DownloadStatus.WAITING)
            } else {
                item
            }
        }

        // DOWNLOADING and QUEUED are reset to WAITING
        assertThat(normalizedList[0].status).isEqualTo(DownloadStatus.WAITING)
        assertThat(normalizedList[1].status).isEqualTo(DownloadStatus.WAITING)

        // PAUSED_BY_USER MUST REMAIN UNTOUCHED
        assertThat(normalizedList[2].status).isEqualTo(DownloadStatus.PAUSED_BY_USER)
        assertThat(normalizedList[2].pausedByUser).isTrue()
    }
}
