package com.lonerxone.siphon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.repository.WaitlistScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var scheduler: WaitlistScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Resets in-flight items back to WAITING without touching pausedByUser
                    downloadDao.normalizeStuckDownloads()

                    // Re-evaluate network and auto-start if usable Wi-Fi is connected
                    val netState = networkMonitor.getCurrentState()
                    scheduler.evaluateQueue(netState)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
