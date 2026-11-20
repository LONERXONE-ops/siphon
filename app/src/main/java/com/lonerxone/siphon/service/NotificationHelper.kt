package com.lonerxone.siphon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lonerxone.siphon.R
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_DOWNLOADS = "siphon_active_downloads"
        const val CHANNEL_WAITLIST = "siphon_waitlist_alerts"
        const val DOWNLOAD_NOTIFICATION_ID = 1001
        const val WAITLIST_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_downloads_desc)
                setShowBadge(false)
            }

            val waitlistChannel = NotificationChannel(
                CHANNEL_WAITLIST,
                context.getString(R.string.channel_waitlist),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_waitlist_desc)
            }

            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(waitlistChannel)
        }
    }

    fun buildDownloadProgressNotification(
        item: DownloadItem,
        speedFormatted: String,
        etaFormatted: String
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_PAUSE
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressInt = item.progress.toInt().coerceIn(0, 100)
        val contentText = "$speedFormatted • $etaFormatted"

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setProgress(100, progressInt, progressInt == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.action_pause),
                pauseIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_cancel),
                cancelIntent
            )
            .build()
    }

    fun showWaitlistCompletedNotification(count: Int, totalBytesFormatted: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WAITLIST)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloads Completed")
            .setContentText("$count downloads done, $totalBytesFormatted saved to SD card")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(WAITLIST_NOTIFICATION_ID, notification)
    }

    fun updateProgressNotification(item: DownloadItem, speedFormatted: String, etaFormatted: String) {
        val notification = buildDownloadProgressNotification(item, speedFormatted, etaFormatted)
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun cancelDownloadNotification() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }
}
