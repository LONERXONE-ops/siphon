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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.ui.components.CircleActionButton
import com.lonerxone.siphon.ui.components.MediaThumbnail
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.components.SiphonStatusChip
import com.lonerxone.siphon.ui.components.formatFileSize
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
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

    var showBatchDialog by remember { mutableStateOf(false) }

    // Started downloads that were paused live on the Home screen, not here
    val shown = waitlistItems.filter { !(it.progress > 0f && it.status != DownloadStatus.WAITING) }
    val totalBytes = shown.sumOf { it.totalBytes }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Waitlist",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        val summary = if (shown.isEmpty()) {
                            "Nothing waiting"
                        } else if (totalBytes > 0L) {
                            "${shown.size} waiting  •  about ${formatFileSize(totalBytes)}"
                        } else {
                            "${shown.size} waiting"
                        }
                        Text(text = summary, fontSize = 12.sp, color = TextSecondary)
                    }

                    SiphonSecondaryButton(
                        text = "Add links",
                        onClick = { showBatchDialog = true },
                        icon = {
                            Icon(
                                Icons.Default.AddLink,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                StatusBanner(
                    networkState = networkState,
                    activeCount = activeDownloads.size,
                    waitlistCount = shown.size
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillAction(label = "Hold all", onClick = { viewModel.pauseAll() })
                    PillAction(label = "Start all", onClick = { viewModel.resumeAll() })
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (shown.isEmpty()) {
                item {
                    SiphonCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(DarkElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Your waitlist is empty", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Choose Download later on any link and it starts by itself on Wi-Fi.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(shown, key = { it.id }) { item ->
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

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
private fun PillAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
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
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (title, subtitle, dotColor) = when {
        activeCount > 0 -> Triple("Downloading $activeCount", "Working through your list on Wi-Fi.", AccentSuccess)
        networkState.isUsableForDownload -> Triple(
            "Wi-Fi connected",
            if (waitlistCount > 0) "Starting your waitlist..." else "New waitlist items start right away.",
            AccentSuccess
        )
        networkState.isMetered && !networkState.isWifi -> Triple(
            "On mobile data",
            "Waitlist downloads wait for Wi-Fi.",
            AccentWarning
        )
        else -> Triple("Waiting for Wi-Fi", "Downloads start automatically when you connect.", TextSecondary)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alphaAnim))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WaitlistCard(
    item: DownloadItem,
    onStartNow: () -> Unit,
    onPauseToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val isHeld = item.status == DownloadStatus.PAUSED_BY_USER

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Any Wi-Fi", fontSize = 11.sp, color = TextSecondary)
                    }
                    if (item.totalBytes > 0L) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatFileSize(item.totalBytes),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        if (!item.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.errorMessage,
                fontSize = 11.sp,
                color = AccentWarning
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(
                icon = Icons.Default.PlayArrow,
                description = "Start now",
                filled = true,
                onClick = onStartNow
            )
            Spacer(modifier = Modifier.width(8.dp))
            CircleActionButton(
                icon = if (isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                description = if (isHeld) "Release hold" else "Hold",
                onClick = onPauseToggle
            )
            Spacer(modifier = Modifier.width(8.dp))
            CircleActionButton(
                icon = Icons.Default.Delete,
                description = "Remove",
                onClick = onRemove
            )
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
            Text("Add links", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    "Paste your links below, one per line. They all go to the waitlist.",
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
                        focusedBorderColor = TextSecondary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            SiphonPrimaryButton(
                text = "Add to waitlist",
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