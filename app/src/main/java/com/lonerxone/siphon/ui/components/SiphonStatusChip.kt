package com.lonerxone.siphon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.ui.theme.AccentError
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun SiphonStatusChip(
    status: DownloadStatus,
    modifier: Modifier = Modifier
) {
    val (label, dotColor) = when (status) {
        DownloadStatus.WAITING -> "Waiting" to TextSecondary
        DownloadStatus.QUEUED -> "Queued" to TextSecondary
        DownloadStatus.DOWNLOADING -> "Downloading" to TextPrimary
        DownloadStatus.PAUSED_BY_USER -> "Paused" to AccentWarning
        DownloadStatus.PAUSED_NO_WIFI -> "Waiting for Wi-Fi" to AccentWarning
        DownloadStatus.PAUSED_NO_STORAGE -> "No storage" to AccentError
        DownloadStatus.MOVING -> "Saving" to TextPrimary
        DownloadStatus.COMPLETED -> "Done" to AccentSuccess
        DownloadStatus.FAILED -> "Failed" to AccentError
        DownloadStatus.CANCELLED -> "Cancelled" to TextSecondary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (status == DownloadStatus.COMPLETED) AccentSuccess else TextPrimary
        )
    }
}