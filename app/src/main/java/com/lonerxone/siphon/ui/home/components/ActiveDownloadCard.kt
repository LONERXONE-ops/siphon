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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.ui.components.GradientProgressBar
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonStatusChip
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

    SiphonCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = item.thumbnailUrl ?: android.R.drawable.stat_sys_download,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiphonStatusChip(status = item.status)

                    val speedText = if (item.speedBps > 0) {
                        val mb = item.speedBps / (1024.0 * 1024.0)
                        String.format("%.1f MB/s", mb)
                    } else "--"

                    Text(
                        text = speedText,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (isPaused) onResume(item.id) else onPause(item.id)
                    }
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = TextPrimary
                    )
                }

                IconButton(onClick = { onCancel(item.id) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        GradientProgressBar(progressPercent = item.progress)

        Spacer(modifier = Modifier.height(6.dp))

        // Progress text: % and ETA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${item.progress.toInt()}%",
                fontSize = 11.sp,
                color = TextSecondary
            )

            val etaText = if (item.etaSeconds > 0) {
                val m = item.etaSeconds / 60
                val s = item.etaSeconds % 60
                "${m}m ${s}s left"
            } else ""

            Text(
                text = etaText,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
