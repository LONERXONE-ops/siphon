package com.lonerxone.siphon.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.ui.components.CircleActionButton
import com.lonerxone.siphon.ui.components.GradientProgressBar
import com.lonerxone.siphon.ui.components.MediaThumbnail
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonStatusChip
import com.lonerxone.siphon.ui.components.formatFileSize
import com.lonerxone.siphon.ui.components.formatTimeLeft
import com.lonerxone.siphon.ui.components.formatTransferSpeed
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPaused = item.status == DownloadStatus.PAUSED_BY_USER ||
        item.status == DownloadStatus.PAUSED_NO_WIFI ||
        item.status == DownloadStatus.PAUSED_NO_STORAGE
    val isMoving = item.status == DownloadStatus.MOVING

    val statusLine = when (item.status) {
        DownloadStatus.DOWNLOADING -> listOf(
            formatTransferSpeed(item.speedBps),
            formatTimeLeft(item.etaSeconds)
        ).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Starting..." }
        DownloadStatus.PAUSED_BY_USER -> "Paused"
        DownloadStatus.PAUSED_NO_WIFI -> "Waiting for Wi-Fi"
        DownloadStatus.PAUSED_NO_STORAGE -> "Storage not available"
        DownloadStatus.MOVING -> "Saving file..."
        else -> "Starting..."
    }

    val progressText = if (item.totalBytes > 0L) {
        "${item.progress.toInt()}%  •  ${formatFileSize(item.bytesDownloaded)} of ${formatFileSize(item.totalBytes)}"
    } else {
        "${item.progress.toInt()}%"
    }

    SiphonCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(
                item = item,
                modifier = Modifier
                    .size(width = 96.dp, height = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiphonStatusChip(status = item.status)
                    if (item.site.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.site,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GradientProgressBar(progressPercent = item.progress)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusLine,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (!isMoving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleActionButton(
                        icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        description = if (isPaused) "Resume" else "Pause",
                        filled = isPaused,
                        onClick = { if (isPaused) onResume(item.id) else onPause(item.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CircleActionButton(
                        icon = Icons.Default.Close,
                        description = "Cancel",
                        onClick = { onCancel(item.id) }
                    )
                }
            }
        }
    }
}