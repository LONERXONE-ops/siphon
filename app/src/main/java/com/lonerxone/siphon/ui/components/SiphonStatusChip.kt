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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.ui.theme.AccentError
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.PrimaryGradientEnd
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun SiphonStatusChip(
    status: DownloadStatus,
    modifier: Modifier = Modifier
) {
    val (label, dotColor, badgeBg) = when (status) {
        DownloadStatus.WAITING -> Triple("Waiting", Color(0xFFA78BFA), DarkElevated)
        DownloadStatus.QUEUED -> Triple("Queued", PrimaryGradientStart, DarkElevated)
        DownloadStatus.DOWNLOADING -> Triple("Downloading", PrimaryGradientEnd, Color(0xFF162D3D))
        DownloadStatus.PAUSED_BY_USER -> Triple("Paused by you", AccentWarning, Color(0xFF332A15))
        DownloadStatus.PAUSED_NO_WIFI -> Triple("Waiting for Wi-Fi", Color(0xFF38BDF8), Color(0xFF142838))
        DownloadStatus.PAUSED_NO_STORAGE -> Triple("No SD Storage", Color(0xFFFB923C), Color(0xFF361E14))
        DownloadStatus.MOVING -> Triple("Saving to SD card", Color(0xFF60A5FA), DarkElevated)
        DownloadStatus.COMPLETED -> Triple("Done", AccentSuccess, Color(0xFF143026))
        DownloadStatus.FAILED -> Triple("Failed", AccentError, Color(0xFF381A1A))
        DownloadStatus.CANCELLED -> Triple("Cancelled", TextSecondary, DarkElevated)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(badgeBg)
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
