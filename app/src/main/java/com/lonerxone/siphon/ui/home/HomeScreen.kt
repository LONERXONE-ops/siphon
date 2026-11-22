package com.lonerxone.siphon.ui.home

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.ui.components.MediaThumbnail
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.components.describeSaveLocation
import com.lonerxone.siphon.ui.components.formatFileSize
import com.lonerxone.siphon.ui.components.mediaKindOf
import com.lonerxone.siphon.ui.components.openMedia
import com.lonerxone.siphon.ui.home.components.ActiveDownloadCard
import com.lonerxone.siphon.ui.home.components.AddLinkBottomSheet
import com.lonerxone.siphon.ui.theme.AccentError
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    sharedUrl: String? = null
) {
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val recentCompleted by viewModel.recentCompleted.collectAsState()
    val addSheetState by viewModel.addSheetState.collectAsState()
    val engineUpdating by viewModel.engineUpdating.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val context = LocalContext.current

    var urlInput by remember { mutableStateOf("") }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var batteryCardDismissed by remember { mutableStateOf(false) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val showBatteryCard = !batteryCardDismissed &&
        !powerManager.isIgnoringBatteryOptimizations(context.packageName)

    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.startsWith("http://") || text.startsWith("https://")) {
                clipboardUrl = text
            }
        }
    }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            urlInput = sharedUrl
            viewModel.fetchLinkInfo(sharedUrl)
        }
    }

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
                            text = "SIPHON",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Download anything",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    NetworkPill(isWifi = networkState.isWifi)
                }

                Spacer(modifier = Modifier.height(22.dp))

                LinkInputCard(
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    clipboardUrl = clipboardUrl,
                    onPaste = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) urlInput = text
                    },
                    onInspect = {
                        if (urlInput.isNotBlank()) viewModel.fetchLinkInfo(urlInput.trim())
                    },
                    onUseClipboard = {
                        clipboardUrl?.let {
                            urlInput = it
                            viewModel.fetchLinkInfo(it)
                        }
                    }
                )

                if (showBatteryCard) {
                    Spacer(modifier = Modifier.height(14.dp))
                    BackgroundAccessCard(
                        onAllow = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                            }
                            batteryCardDismissed = true
                        },
                        onDismiss = { batteryCardDismissed = true }
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))
                SectionHeader(title = "Downloading", count = activeDownloads.size)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (activeDownloads.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Download,
                        title = "Nothing downloading",
                        subtitle = "Paste a link above, or open the Browser tab to find media on any site."
                    )
                }
            } else {
                items(activeDownloads, key = { it.id }) { item ->
                    ActiveDownloadCard(
                        item = item,
                        onPause = { viewModel.pauseDownload(it) },
                        onResume = { viewModel.resumeDownload(it) },
                        onCancel = { viewModel.cancelDownload(it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (failedDownloads.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    SectionHeader(title = "Needs attention", count = failedDownloads.size)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(failedDownloads, key = { "failed-" + it.id }) { item ->
                    FailedDownloadCard(
                        item = item,
                        engineUpdating = engineUpdating,
                        onRetry = { viewModel.retryDownload(item.id) },
                        onRemove = { viewModel.cancelDownload(item.id) },
                        onUpdateEngine = { viewModel.updateEngine() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
                SectionHeader(title = "Recently completed", count = null)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (recentCompleted.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Folder,
                        title = "No downloads yet",
                        subtitle = "Finished downloads will show up here."
                    )
                }
            } else {
                items(recentCompleted, key = { it.id }) { item ->
                    RecentCompletedRow(item = item)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        AddLinkBottomSheet(
            uiState = addSheetState,
            onDismiss = { viewModel.dismissSheet() },
            onDownloadNow = { meta, formatId, label ->
                viewModel.onDownloadNowClicked(meta, formatId, label)
            },
            onDownloadLater = { meta, formatId, label ->
                viewModel.downloadLater(meta, formatId, label)
            },
            onConfirmMobileData = { meta, formatId ->
                viewModel.confirmMobileDataDownload(meta, formatId)
            },
            onDownloadSelected = { meta, ids, later ->
                viewModel.onDownloadSelected(meta, ids, later)
            }
        )
    }
}

@Composable
private fun NetworkPill(isWifi: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isWifi) AccentSuccess else AccentWarning)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isWifi) "Wi-Fi" else "No Wi-Fi",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun LinkInputCard(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    clipboardUrl: String?,
    onPaste: () -> Unit,
    onInspect: () -> Unit,
    onUseClipboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Paste a link",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Videos, music, images and files from almost any site.",
            fontSize = 12.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            placeholder = { Text("https://...", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBackground,
                unfocusedContainerColor = DarkBackground,
                focusedBorderColor = TextSecondary,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = TextPrimary
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (urlInput.isBlank()) onPaste() else onUrlChange("")
                    }
                ) {
                    Icon(
                        imageVector = if (urlInput.isBlank()) Icons.Default.ContentPaste else Icons.Default.Close,
                        contentDescription = if (urlInput.isBlank()) "Paste" else "Clear",
                        tint = TextSecondary
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onInspect() })
        )

        if (!clipboardUrl.isNullOrBlank() && urlInput != clipboardUrl) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkElevated)
                    .clickable(onClick = onUseClipboard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Link in clipboard", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = clipboardUrl,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "USE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SiphonPrimaryButton(
            text = "Inspect link",
            onClick = onInspect,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BackgroundAccessCard(onAllow: () -> Unit, onDismiss: () -> Unit) {
    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = AccentWarning,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Keep downloads running",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Allow SIPHON to work in the background so Wi-Fi downloads can start on their own.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SiphonPrimaryButton(
                text = "Allow",
                onClick = onAllow,
                modifier = Modifier.weight(1f)
            )
            SiphonSecondaryButton(
                text = "Not now",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (count != null && count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkElevated)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: ImageVector, title: String, subtitle: String) {
    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DarkElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun FailedDownloadCard(
    item: DownloadItem,
    engineUpdating: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onUpdateEngine: () -> Unit
) {
    val message = item.errorMessage?.takeIf { it.isNotBlank() } ?: "Something went wrong."
    val engineRelated = message.contains("engine", ignoreCase = true) ||
        message.contains("isn't supported", ignoreCase = true)

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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = AccentError,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SiphonPrimaryButton(
                text = "Retry",
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            )
            if (engineRelated) {
                SiphonSecondaryButton(
                    text = if (engineUpdating) "Updating..." else "Update engine",
                    onClick = onUpdateEngine,
                    enabled = !engineUpdating,
                    modifier = Modifier.weight(1f)
                )
            }
            SiphonSecondaryButton(
                text = "Remove",
                onClick = onRemove,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun RecentCompletedRow(item: DownloadItem) {
    val context = LocalContext.current
    val sizeBytes = maxOf(item.totalBytes, item.bytesDownloaded)
    val kind = mediaKindOf(item.formatLabel, item.title)
    val meta = listOf(
        item.site,
        if (sizeBytes > 0L) formatFileSize(sizeBytes) else "",
        describeSaveLocation(item.finalUri)
    ).filter { it.isNotBlank() }.joinToString("  •  ")

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.finalUri != null) {
                    item.finalUri?.let { openMedia(context, it, item.title) }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = kind,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.finalUri != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Open",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}