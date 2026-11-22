package com.lonerxone.siphon.data.repository

import android.content.Context
import android.content.Intent
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.DownloadEngineRouter
import com.lonerxone.siphon.data.engine.PageMediaFinder
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.storage.StorageManager
import com.lonerxone.siphon.data.storage.StorageStatus
import com.lonerxone.siphon.service.DownloadService
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val engineRouter: DownloadEngineRouter,
    private val storageManager: StorageManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor,
    private val scheduler: WaitlistScheduler,
    private val pageMediaFinder: PageMediaFinder
) : DownloadRepository {

    override val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    override val activeDownloads: Flow<List<DownloadItem>> = downloadDao.getActiveDownloads()
    override val waitlistDownloads: Flow<List<DownloadItem>> = downloadDao.getWaitlistDownloads()
    override val recentCompleted: Flow<List<DownloadItem>> = downloadDao.getRecentCompleted()
    override val allCompleted: Flow<List<DownloadItem>> = downloadDao.getAllCompleted()

    init {
        // Wire scheduler dispatch callback to start service
        scheduler.onDispatchItem = { item ->
            startDownloadService(item.id)
        }
        scheduler.onPauseItemForNoWifi = { id ->
            val engine = engineRouter.selectEngine("")
            CoroutineScope(Dispatchers.IO).launch {
                val pausedItem = downloadDao.getById(id)
                if (pausedItem != null && !pausedItem.mobileDataApproved) {
                    engineRouter.selectEngine(pausedItem.url).pause(id)
                }
            }
        }
    }

    override suspend fun fetchMetadata(url: String): Result<MediaMetadata> {
        // Fetching info is always allowed on any network, including mobile data (PRD 5.1)
        val engine = engineRouter.selectEngine(url)
        val primary = engine.fetchMetadata(url)
        if (primary.isSuccess) return primary

        // The engine could not read the link: look for images and videos on the page itself
        val fallback = pageMediaFinder.find(url)
        return if (fallback.isSuccess) fallback else primary
    }

    override suspend fun downloadNow(
        url: String,
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        mobileDataApproved: Boolean
    ): Result<DownloadItem> = withContext(Dispatchers.IO) {
        val netState = networkMonitor.getCurrentState()
        val prefs = preferencesRepository.userPreferencesFlow.first()

        val format = metadata.formats.find { it.formatId == formatId }
            ?: metadata.formats.firstOrNull()
        val estimatedSize = format?.fileSizeApprox ?: 0L

        // Storage pre-flight check: 2x free space required
        val storageCheck = storageManager.checkStorageStatus(estimatedSize)
        if (storageCheck is StorageStatus.InsufficientSpace) {
            val reqMb = storageCheck.requiredBytes / (1024 * 1024)
            val freeMb = storageCheck.freeBytes / (1024 * 1024)
            return@withContext Result.failure(
                IllegalStateException("Not enough free storage. Needs about ${reqMb}MB but only ${freeMb}MB is available.")
            )
        }

        val engineType = engineRouter.determineEngineType(url)
        val maxPos = downloadDao.getMaxPosition() ?: 0

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = metadata.title,
            thumbnailUrl = metadata.thumbnailUrl,
            site = metadata.site,
            engine = engineType,
            formatId = formatId,
            formatLabel = formatLabel ?: format?.label,
            status = DownloadStatus.QUEUED,
            pausedByUser = false,
            totalBytes = estimatedSize,
            mobileDataApproved = mobileDataApproved,
            position = maxPos + 1
        )

        downloadDao.insert(item)

        // Remember last used format
        formatId?.let { preferencesRepository.setLastUsedFormat(it) }

        // Start Foreground Service
        startDownloadService(item.id)

        Result.success(item)
    }

    override suspend fun downloadLater(
        url: String,
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        ruleId: String?
    ): Result<DownloadItem> = withContext(Dispatchers.IO) {
        val engineType = engineRouter.determineEngineType(url)
        val maxPos = downloadDao.getMaxPosition() ?: 0
        val format = metadata.formats.find { it.formatId == formatId }
            ?: metadata.formats.firstOrNull()

        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = metadata.title,
            thumbnailUrl = metadata.thumbnailUrl,
            site = metadata.site,
            engine = engineType,
            formatId = formatId,
            formatLabel = formatLabel ?: format?.label,
            status = DownloadStatus.WAITING,
            pausedByUser = false,
            totalBytes = format?.fileSizeApprox ?: 0L,
            ruleId = ruleId,
            position = maxPos + 1
        )

        downloadDao.insert(item)
        formatId?.let { preferencesRepository.setLastUsedFormat(it) }

        // Trigger scheduler evaluation: if conditions are met, it will start immediately
        val currentNet = networkMonitor.getCurrentState()
        scheduler.evaluateQueue(currentNet)

        Result.success(item)
    }

    override suspend fun addBatchLinks(urls: List<String>): Int = withContext(Dispatchers.IO) {
        var added = 0
        val maxPos = downloadDao.getMaxPosition() ?: 0

        for ((index, url) in urls.withIndex()) {
            if (url.isBlank()) continue
            val engineType = engineRouter.determineEngineType(url)
            val item = DownloadItem(
                id = UUID.randomUUID().toString(),
                url = url.trim(),
                title = "Batch link: ${url.trim().take(30)}...",
                site = "Web",
                engine = engineType,
                status = DownloadStatus.WAITING,
                pausedByUser = false,
                position = maxPos + index + 1
            )
            downloadDao.insert(item)
            added++
        }

        scheduler.evaluateQueue(networkMonitor.getCurrentState())
        added
    }

    override suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        // CRITICAL INVARIANT: Manual pause marks pausedByUser = true
        downloadDao.updateStatus(id, DownloadStatus.PAUSED_BY_USER, pausedByUser = true)
        val engine = engineRouter.selectEngine("")
        (downloadDao.getById(id)?.let { engineRouter.selectEngine(it.url) } ?: engine).pause(id); Unit
    }

    override suspend fun resumeDownload(id: String) = withContext(Dispatchers.IO) {
        // User explicitly taps Resume: resets pausedByUser = false
        val item = downloadDao.getById(id) ?: return@withContext
        val netState = networkMonitor.getCurrentState()

        if (netState.isUsableForDownload || item.mobileDataApproved) {
            downloadDao.updateStatus(id, DownloadStatus.QUEUED, pausedByUser = false)
            startDownloadService(id)
        } else {
            downloadDao.updateStatus(id, DownloadStatus.WAITING, pausedByUser = false)
            scheduler.evaluateQueue(netState)
        }
    }

    override suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        val item = downloadDao.getById(id)
        if (item != null) {
            val engine = engineRouter.selectEngine(item.url)
            engine.cancel(id)
            // Clean up staging files if any
            item.stagingPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteById(id)
        }
    }

    override suspend fun pauseAll() = withContext(Dispatchers.IO) {
        val activeNow = downloadDao.getActiveDownloads().first()
        val pausedResult = downloadDao.pauseAll()
        activeNow.forEach { engineRouter.selectEngine(it.url).pause(it.id) }
        pausedResult
    }

    override suspend fun resumeAll() = withContext(Dispatchers.IO) {
        downloadDao.resumeAll()
        scheduler.evaluateQueue(networkMonitor.getCurrentState())
    }

    override suspend fun deleteDownload(id: String, deleteFile: Boolean) = withContext(Dispatchers.IO) {
        val item = downloadDao.getById(id)
        if (item != null) {
            if (deleteFile) {
                deleteFinalFile(item.finalUri)
            }
            if (deleteFile && item.stagingPath != null) {
                val f = File(item.stagingPath)
                if (f.exists()) f.delete()
            }
            downloadDao.deleteById(id)
        }
    }

    override suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Uses youtubedl-android built-in yt-dlp binary update
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            Result.success("yt-dlp engine updated successfully: $status")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update yt-dlp: ${e.message}", e))
        }
    }

    override suspend fun normalizeStuckDownloads() = withContext(Dispatchers.IO) {
        downloadDao.normalizeStuckDownloads()
    }

    override suspend fun getDownloadById(id: String): DownloadItem? {
        return downloadDao.getById(id)
    }

    private fun deleteFinalFile(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val uri = android.net.Uri.parse(uriString)
            if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            // The file may already be gone, or Android refuses to delete a file we do not own
        }
    }

    private fun startDownloadService(downloadId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // Android can block starting a download service from the background
            CoroutineScope(Dispatchers.IO).launch {
                downloadDao.updateStatus(
                    downloadId,
                    DownloadStatus.WAITING,
                    pausedByUser = false,
                    errorMessage = "Could not start in the background. Open SIPHON to continue."
                )
            }
        }
    }
}
