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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.update.UpdateUiState
import com.lonerxone.siphon.ui.components.BrandMark
import com.lonerxone.siphon.ui.components.MediaThumbnail
import com.lonerxone.siphon.ui.components.LinkOmnibox
import com.lonerxone.siphon.ui.components.NetworkIndicator
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
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.OnAccent
import com.lonerxone.siphon.ui.theme.SiphonPrimaryGradient
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    sharedUrl: String? = null,
    onOpenBrowser: () -> Unit = {}
) {
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val recentCompleted by viewModel.recentCompleted.collectAsState()
    val addSheetState by viewModel.addSheetState.collectAsState()
    val engineUpdating by viewModel.engineUpdating.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    var urlInput by remember { mutableStateOf("") }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var batteryCardDismissed by remember { mutableStateOf(false) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val showBatteryCard = !batteryCardDismissed &&
        !powerManager.isIgnoringBatteryOptimizations(context.packageName)

    // Look at the clipboard every time the user comes back to the app
    LifecycleResumeEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            clipboardUrl = if (text.startsWith("http://") || text.startsWith("https://")) text else null
        }
        onPauseOrDispose { }
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
                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(boxSize = 46.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SIPHON",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 4.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Download anything",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    NetworkPill(isWifi = networkState.isWifi)
                }

                Spacer(modifier = Modifier.height(22.dp))

                UpdateBanner(
                    state = updateState,
                    onDownload = { viewModel.downloadUpdate() },
                    onInstall = { viewModel.installUpdate() }
                )

                LinkOmnibox(
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    onPaste = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) urlInput = text
                    },
                    onInspect = {
                        if (urlInput.isNotBlank()) viewModel.fetchLinkInfo(urlInput.trim())
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!clipboardUrl.isNullOrBlank() && urlInput != clipboardUrl) {
                        ActionChip(
                            icon = Icons.Default.ContentPaste,
                            label = "Use link from clipboard",
                            onClick = {
                                clipboardUrl?.let {
                                    urlInput = it
                                    viewModel.fetchLinkInfo(it)
                                }
                            }
                        )
                    }
                    ActionChip(
                        icon = Icons.Default.Language,
                        label = "Find media in the browser",
                        onClick = onOpenBrowser
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Video  •  Audio  •  Images  •  Files",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                if (showBatteryCard) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                        subtitle = "Paste a link above, or use the browser to find media on any site."
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkIndicator(isWifi = isWifi, iconSize = 18.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isWifi) "Wi-Fi" else "Mobile data",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun LegacyLinkOmnibox(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onInspect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(32.dp))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        BasicTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onGo = { onInspect() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Paste a link to download",
                            color = TextMuted,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )

        IconButton(
            onClick = { if (urlInput.isBlank()) onPaste() else onUrlChange("") },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (urlInput.isBlank()) Icons.Default.ContentPaste else Icons.Default.Close,
                contentDescription = if (urlInput.isBlank()) "Paste" else "Clear",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SiphonPrimaryGradient)
                .clickable(onClick = onInspect),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Inspect link",
                tint = OnAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun UpdateBanner(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val show = state is UpdateUiState.Available ||
        state is UpdateUiState.Downloading ||
        state is UpdateUiState.ReadyToInstall ||
        state is UpdateUiState.NeedsPermission
    if (!show) return

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                when (state) {
                    is UpdateUiState.Downloading -> {
                        Text("Downloading update", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${state.percent}%", fontSize = 12.sp, color = TextSecondary)
                    }
                    is UpdateUiState.Available -> {
                        Text("Update available", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Build ${state.buildNumber}  •  ${formatFileSize(state.sizeBytes)}", fontSize = 12.sp, color = TextSecondary)
                    }
                    else -> {
                        Text("Update ready", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Tap install to get the newest version.", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }

        if (state is UpdateUiState.Downloading) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = TextPrimary,
                trackColor = DarkElevated
            )
        } else if (state is UpdateUiState.Available) {
            Spacer(modifier = Modifier.height(12.dp))
            SiphonSecondaryButton(
                text = if (state.metered) "Download (uses mobile data)" else "Download",
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            SiphonPrimaryButton(
                text = "Install update",
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
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