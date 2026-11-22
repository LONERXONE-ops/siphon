package com.lonerxone.siphon.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.lonerxone.siphon.ui.components.formatFileSize
import com.lonerxone.siphon.ui.components.formatTimeLeft
import com.lonerxone.siphon.ui.components.formatTransferSpeed
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkElevated
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
    val isRunning = item.status == DownloadStatus.DOWNLOADING

    val stateText = when (item.status) {
        DownloadStatus.PAUSED_BY_USER -> "Paused"
        DownloadStatus.PAUSED_NO_WIFI -> "Waiting for Wi-Fi"
        DownloadStatus.PAUSED_NO_STORAGE -> "Storage not available"
        DownloadStatus.MOVING -> "Saving file..."
        DownloadStatus.DOWNLOADING -> "Downloading"
        else -> "Starting..."
    }

    val subtitle = listOf(item.site, item.formatLabel.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString("  •  ")

    SiphonCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            // thumbnail with a progress ring and the pause / resume control on top
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !isMoving) {
                        if (isPaused) onResume(item.id) else onPause(item.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                if (isMoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeWidth = 3.dp
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(44.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeWidth = 3.dp
                    )
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CircleActionButton(
                        icon = Icons.Default.Close,
                        description = "Cancel",
                        size = 30.dp,
                        onClick = { onCancel(item.id) }
                    )
                }

                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GradientProgressBar(progressPercent = item.progress)

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatPill(text = "${item.progress.toInt()}%", highlight = true)
                    if (isRunning) {
                        val speed = formatTransferSpeed(item.speedBps)
                        val eta = formatTimeLeft(item.etaSeconds)
                        if (speed.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatPill(text = speed)
                        }
                        if (eta.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatPill(text = eta)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stateText,
                            fontSize = 11.sp,
                            color = if (item.status == DownloadStatus.PAUSED_NO_STORAGE) AccentWarning else TextSecondary
                        )
                    }
                }

                if (item.totalBytes > 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${formatFileSize(item.bytesDownloaded)} of ${formatFileSize(item.totalBytes)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkElevated)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) TextPrimary else TextSecondary
        )
    }
}