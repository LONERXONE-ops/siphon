package com.lonerxone.siphon.data.repository

import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.data.storage.StorageManager
import com.lonerxone.siphon.data.storage.StorageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaitlistScheduler @Inject constructor(
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Callback invoked when an item is scheduled to start download
    var onDispatchItem: ((DownloadItem) -> Unit)? = null
    var onPauseItemForNoWifi: ((String) -> Unit)? = null

    fun startListening() {
        scope.launch {
            networkMonitor.networkState.collect { networkState ->
                evaluateQueue(networkState)
            }
        }
    }

    suspend fun evaluateQueue(networkState: NetworkState) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val concurrencyLimit = prefs.concurrencyLimit

        if (networkState.isUsableForDownload) {
            // Usable Wi-Fi is available
            val activeCount = downloadDao.getActiveDownloadCount()
            val availableSlots = (concurrencyLimit - activeCount).coerceAtLeast(0)

            if (availableSlots > 0) {
                /**
                 * CRITICAL INVARIANT:
                 * getSchedulableItems() returns items where pausedByUser = 0.
                 * Any item paused by the user is NEVER returned and will NOT start.
                 */
                val schedulable = downloadDao.getSchedulableItems()
                val toSchedule = schedulable.take(availableSlots)

                for (item in toSchedule) {
                    // Pre-flight 2x free space check on SD card
                    val storageCheck = storageManager.checkStorageStatus(item.totalBytes)
                    when (storageCheck) {
                        is StorageStatus.InsufficientSpace -> {
                            val mbReq = storageCheck.requiredBytes / (1024 * 1024)
                            val mbFree = storageCheck.freeBytes / (1024 * 1024)
                            downloadDao.updateStatus(
                                id = item.id,
                                status = DownloadStatus.PAUSED_NO_STORAGE,
                                pausedByUser = false,
                                errorMessage = "Need ${mbReq}MB free on SD card (2x file size). Only ${mbFree}MB available."
                            )
                        }
                        is StorageStatus.NoSdCardDetected -> {
                            downloadDao.updateStatus(
                                id = item.id,
                                status = DownloadStatus.PAUSED_NO_STORAGE,
                                pausedByUser = false,
                                errorMessage = "No SD card detected. Insert SD card or select internal storage."
                            )
                        }
                        is StorageStatus.Ok -> {
                            downloadDao.updateStatus(
                                id = item.id,
                                status = DownloadStatus.QUEUED,
                                pausedByUser = false,
                                errorMessage = null
                            )
                            onDispatchItem?.invoke(item.copy(status = DownloadStatus.QUEUED))
                        }
                        else -> {
                            // Permission or other error
                        }
                    }
                }
            }
        } else {
            // Wi-Fi is NOT usable (dropped or unvalidated)
            // Pause active downloads that are NOT already paused by user
            val activeItems = downloadDao.getActiveDownloads().first()
            for (item in activeItems) {
                if (!item.pausedByUser && !item.mobileDataApproved && item.status == DownloadStatus.DOWNLOADING) {
                    downloadDao.updateStatus(
                        id = item.id,
                        status = DownloadStatus.PAUSED_NO_WIFI,
                        pausedByUser = false,
                        errorMessage = "Paused: waiting for usable Wi-Fi"
                    )
                    onPauseItemForNoWifi?.invoke(item.id)
                }
            }
        }
    }
}
