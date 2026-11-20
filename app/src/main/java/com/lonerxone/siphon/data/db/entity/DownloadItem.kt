package com.lonerxone.siphon.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class DownloadStatus {
    WAITING,
    QUEUED,
    DOWNLOADING,
    PAUSED_BY_USER,
    PAUSED_NO_WIFI,
    PAUSED_NO_STORAGE,
    MOVING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class EngineType {
    YT_DLP,
    ARIA2C
}

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val site: String = "",
    val engine: EngineType = EngineType.YT_DLP,
    val formatId: String? = null,
    val formatLabel: String? = null,
    val status: DownloadStatus = DownloadStatus.WAITING,
    /**
     * CRITICAL INVARIANT:
     * When true, this download was paused explicitly by the user.
     * It MUST NEVER auto-resume on Wi-Fi connection, network changes, SD card changes, or phone reboots.
     */
    val pausedByUser: Boolean = false,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val stagingPath: String? = null,
    val finalUri: String? = null,
    val errorMessage: String? = null,
    val mobileDataApproved: Boolean = false,
    val ruleId: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
