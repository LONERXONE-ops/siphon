package com.lonerxone.siphon.data.repository

import android.net.Uri
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    val allDownloads: Flow<List<DownloadItem>>
    val activeDownloads: Flow<List<DownloadItem>>
    val waitlistDownloads: Flow<List<DownloadItem>>
    val recentCompleted: Flow<List<DownloadItem>>
    val allCompleted: Flow<List<DownloadItem>>

    suspend fun fetchMetadata(url: String): Result<MediaMetadata>

    suspend fun downloadNow(
        url: String,
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        mobileDataApproved: Boolean = false
    ): Result<DownloadItem>

    suspend fun downloadLater(
        url: String,
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        ruleId: String? = null
    ): Result<DownloadItem>

    suspend fun addBatchLinks(urls: List<String>): Int

    suspend fun pauseDownload(id: String)

    suspend fun resumeDownload(id: String)

    suspend fun cancelDownload(id: String)

    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun deleteDownload(id: String, deleteFile: Boolean = false)

    suspend fun updateEngine(): Result<String>

    suspend fun normalizeStuckDownloads()

    suspend fun getDownloadById(id: String): DownloadItem?
}
