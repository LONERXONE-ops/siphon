package com.lonerxone.siphon.ui.waitlist

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.components.SiphonStatusChip
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.PrimaryGradientEnd
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun WaitlistScreen(
    viewModel: WaitlistViewModel = hiltViewModel()
) {
    val waitlistItems by viewModel.waitlistItems.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val summary by viewModel.summary.collectAsState()

    var showBatchDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WAITLIST",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Batch Paste Button
                    SiphonSecondaryButton(
                        text = "Paste Links",
                        onClick = { showBatchDialog = true },
                        icon = {
                            Icon(Icons.Default.AddLink, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Banner with Pulsing Dot (PRD 5.5)
                StatusBanner(
                    networkState = networkState,
                    activeCount = activeDownloads.size,
                    waitlistCount = waitlistItems.size
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Line: e.g. "5 items • about 2.4 GB"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${summary.itemCount} items • about ${summary.formattedTotalSize}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    // Global Controls: Pause all / Resume all
                    Row {
                        TextButton(onClick = { viewModel.pauseAll() }) {
                            Text("Pause all", color = AccentWarning, fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.resumeAll() }) {
                            Text("Resume all", color = PrimaryGradientEnd, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (waitlistItems.isEmpty()) {
                item {
                    SiphonCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Waitlist is empty", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Links added with 'Download later' appear here.", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(waitlistItems, key = { it.id }) { item ->
                    WaitlistCard(
                        item = item,
                        onStartNow = { viewModel.startNow(item) },
                        onPauseToggle = {
                            if (item.status == DownloadStatus.PAUSED_BY_USER) {
                                viewModel.resumeItem(item.id)
                            } else {
                                viewModel.pauseItem(item.id)
                            }
                        },
                        onRemove = { viewModel.removeItem(item.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Batch Paste Multi-line Dialog (PRD 5.1)
        if (showBatchDialog) {
            BatchPasteDialog(
                onDismiss = { showBatchDialog = false },
                onAddLinks = { text ->
                    viewModel.addBatchLinks(text)
                    showBatchDialog = false
                }
            )
        }
    }
}

@Composable
fun StatusBanner(
    networkState: NetworkState,
    activeCount: Int,
    waitlistCount: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (bannerText, bannerColor) = when {
        activeCount > 0 -> "On Wi-Fi: downloading $activeCount item(s)" to PrimaryGradientEnd
        networkState.isUsableForDownload -> "On Wi-Fi: listening for queue" to PrimaryGradientStart
        networkState.isMetered && !networkState.isWifi -> "On mobile data: waiting for Wi-Fi" to AccentWarning
        else -> "Waiting for Wi-Fi" to Color(0xFF38BDF8)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(bannerColor.copy(alpha = alphaAnim))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = bannerText,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun WaitlistCard(
    item: DownloadItem,
    onStartNow: () -> Unit,
    onPauseToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val isPausedByUser = item.status == DownloadStatus.PAUSED_BY_USER

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiphonStatusChip(status = item.status)
                    Spacer(modifier = Modifier.width(8.dp))

                    // Rule chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBorder)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Any Wi-Fi",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (!item.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.errorMessage,
                        fontSize = 11.sp,
                        color = Color(0xFFFB923C)
                    )
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onStartNow) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start now", tint = PrimaryGradientStart)
                }

                IconButton(onClick = onPauseToggle) {
                    Icon(
                        imageVector = if (isPausedByUser) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Hold",
                        tint = if (isPausedByUser) AccentWarning else TextSecondary
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextMuted)
                }
            }
        }
    }
}

@Composable
fun BatchPasteDialog(
    onDismiss: () -> Unit,
    onAddLinks: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Paste Links", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    "Paste multiple links below (one link per line). They will all be parked in the Waitlist.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("https://...\nhttps://...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = PrimaryGradientStart,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            SiphonPrimaryButton(
                text = "Add to Waitlist",
                onClick = { onAddLinks(rawText) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkElevated
    )
}
