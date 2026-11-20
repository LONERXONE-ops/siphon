package com.lonerxone.siphon.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.home.components.ActiveDownloadCard
import com.lonerxone.siphon.ui.home.components.AddLinkBottomSheet
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
    val recentCompleted by viewModel.recentCompleted.collectAsState()
    val addSheetState by viewModel.addSheetState.collectAsState()
    val context = LocalContext.current

    var urlInput by remember { mutableStateOf("") }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    // Foreground clipboard detection chip (PRD 5.1)
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

    // Auto-trigger if shared from external app (ACTION_SEND)
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
                // App Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SIPHON",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Download Anything",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Paste Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Paste link from anywhere...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = PrimaryGradientStart,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank()) {
                                    urlInput = text
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = TextSecondary)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (urlInput.isNotBlank()) viewModel.fetchLinkInfo(urlInput)
                        }
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Clipboard Chip if detected
                if (!clipboardUrl.isNullOrBlank() && urlInput != clipboardUrl) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "From clipboard: ${clipboardUrl?.take(28)}...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SiphonPrimaryButton(
                            text = "Use",
                            onClick = {
                                clipboardUrl?.let {
                                    urlInput = it
                                    viewModel.fetchLinkInfo(it)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                SiphonPrimaryButton(
                    text = "Inspect Link",
                    onClick = {
                        if (urlInput.isNotBlank()) viewModel.fetchLinkInfo(urlInput)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Active Downloads Section
                Text(
                    text = "ACTIVE DOWNLOADS (${activeDownloads.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (activeDownloads.isEmpty()) {
                item {
                    SiphonCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = TextMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No active downloads", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
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

            // Recent Completed Section
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "RECENT COMPLETED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (recentCompleted.isEmpty()) {
                item {
                    Text(
                        text = "No downloads completed yet.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(recentCompleted, key = { it.id }) { item ->
                    RecentCompletedRow(item = item)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
            }
        }

        // Add Link Bottom Sheet
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
            }
        )
    }
}

@Composable
fun RecentCompletedRow(item: DownloadItem) {
    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.site} • Saved to SD card",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
