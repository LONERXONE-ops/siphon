package com.lonerxone.siphon.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.engine.DownloadEngineRouter
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.repository.WaitlistScheduler
import com.lonerxone.siphon.data.storage.StorageManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var engineRouter: DownloadEngineRouter
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var scheduler: WaitlistScheduler

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START_DOWNLOAD = "com.lonerxone.siphon.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.lonerxone.siphon.action.PAUSE"
        const val ACTION_RESUME = "com.lonerxone.siphon.action.RESUME"
        const val ACTION_CANCEL = "com.lonerxone.siphon.action.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siphon:download_wakelock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)

        when (action) {
            ACTION_START_DOWNLOAD -> {
                if (downloadId != null) {
                    startDownloadJob(downloadId)
                }
            }
            ACTION_PAUSE -> {
                if (downloadId != null) {
                    pauseDownloadJob(downloadId)
                }
            }
            ACTION_CANCEL -> {
                if (downloadId != null) {
                    cancelDownloadJob(downloadId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startDownloadJob(downloadId: String) {
        if (activeJobs.containsKey(downloadId)) return

        val job = serviceScope.launch {
            val item = downloadDao.getById(downloadId) ?: return@launch
            promoteToForeground(item)
            acquireWakeLock()

            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING, pausedByUser = false)

            val stagingDir = File(storageManager.getStagingDirectory(), item.id).also { it.mkdirs() }
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val engine = engineRouter.selectEngine(item.url)

            val request = DownloadRequest(
                id = item.id,
                url = item.url,
                formatId = item.formatId,
                outputDir = stagingDir,
                speedLimitKbps = prefs.speedLimitKbps
            )

            val downloadResult = engine.startDownload(request) { progress ->
                serviceScope.launch {
                    downloadDao.updateProgress(
                        id = item.id,
                        bytes = progress.bytesDownloaded,
                        total = progress.totalBytes,
                        progress = progress.progressPercent,
                        speed = progress.speedBps,
                        eta = progress.etaSeconds
                    )
                    notificationHelper.updateProgressNotification(
                        item = item.copy(progress = progress.progressPercent),
                        speedFormatted = progress.formattedSpeed,
                        etaFormatted = progress.formattedEta
                    )
                }
            }

            downloadResult.fold(
                onSuccess = { stagedFile ->
                    // Transition to MOVING state
                    downloadDao.updateStatus(downloadId, DownloadStatus.MOVING, pausedByUser = false)

                    val targetUriStr = prefs.sdFolderUri ?: "siphon://phone"
                    if (targetUriStr != null) {
                        val targetUri = Uri.parse(targetUriStr)
                        val subfolder = if (prefs.subfolderOrganizeMode == "BY_SITE") item.site else null

                        val moveResult = storageManager.moveToTargetSaf(
                            stagingFile = stagedFile,
                            targetTreeUri = targetUri,
                            subfolderName = subfolder
                        )

                        moveResult.fold(
                            onSuccess = { finalUri ->
                                downloadDao.update(
                                    item.copy(
                                        status = DownloadStatus.COMPLETED,
                                        stagingPath = null,
                                        finalUri = finalUri.toString(),
                                        completedAt = System.currentTimeMillis(),
                                        progress = 100f
                                    )
                                )
                            },
                            onFailure = { err ->
                                downloadDao.updateStatus(
                                    downloadId,
                                    DownloadStatus.FAILED,
                                    pausedByUser = false,
                                    errorMessage = "Could not save the file: ${err.message}"
                                )
                            }
                        )
                    } else {
                        // User hasn't configured SAF folder yet, keep in staging
                        downloadDao.update(
                            item.copy(
                                status = DownloadStatus.COMPLETED,
                                stagingPath = stagedFile.absolutePath,
                                completedAt = System.currentTimeMillis(),
                                progress = 100f
                            )
                        )
                    }
                },
                onFailure = { err ->
                    val st = downloadDao.getById(downloadId)?.status
                    val isPaused = st == DownloadStatus.PAUSED_BY_USER || st == DownloadStatus.PAUSED_NO_WIFI
                    if (!isPaused) {
                        downloadDao.updateStatus(
                            downloadId,
                            DownloadStatus.FAILED,
                            pausedByUser = false,
                            errorMessage = err.message
                        )
                    }
                }
            )

            activeJobs.remove(downloadId)
            checkAllJobsFinished()
        }

        activeJobs[downloadId] = job
    }

    private fun pauseDownloadJob(downloadId: String) {
        serviceScope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED_BY_USER, pausedByUser = true)
            val engine = downloadDao.getById(downloadId)?.let { engineRouter.selectEngine(it.url) } ?: engineRouter.selectEngine("")
            engine.pause(downloadId)
            activeJobs[downloadId]?.cancel()
            activeJobs.remove(downloadId)
            checkAllJobsFinished()
        }
    }

    private fun cancelDownloadJob(downloadId: String) {
        serviceScope.launch {
            val engine = downloadDao.getById(downloadId)?.let { engineRouter.selectEngine(it.url) } ?: engineRouter.selectEngine("")
            engine.cancel(downloadId)
            activeJobs[downloadId]?.cancel()
            activeJobs.remove(downloadId)
            downloadDao.deleteById(downloadId)
            File(storageManager.getStagingDirectory(), downloadId).deleteRecursively()
            checkAllJobsFinished()
        }
    }

    private fun promoteToForeground(item: DownloadItem) {
        val notification = notificationHelper.buildDownloadProgressNotification(
            item = item,
            speedFormatted = "Starting...",
            etaFormatted = ""
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun checkAllJobsFinished() {
        if (activeJobs.isEmpty()) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            // Trigger scheduler for any remaining waitlist items
            serviceScope.launch {
                scheduler.evaluateQueue(networkMonitor.getCurrentState())
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hour max safety timeout
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    /**
     * Android 15 (API 35) 6-Hour Timeout Safety Net.
     * Not an @Override since we target SDK 34; this method is a no-op on older SDKs.
     * On API 35+ the system will call this via reflection when the FGS dataSync limit is hit.
     */
    @Suppress("unused")
    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJobs.forEach { (id, job) ->
            job.cancel()
        }
        activeJobs.clear()
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
