package com.lonerxone.siphon.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.theme.AccentError
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.PrimaryGradientEnd
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary
import com.yausername.youtubedl_android.YoutubeDL

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val engineState by viewModel.engineUpdateState.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setSdFolderUri(uri)
        }
    }

    // Battery optimisation status, refreshed every time the user comes back to the app
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryUnrestricted by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }

    val engineVersion = remember(engineState) {
        try {
            YoutubeDL.getInstance().versionName(context)
        } catch (e: Exception) {
            null
        }
    }

    val savedUri = prefs.sdFolderUri
    val usingPhoneStorage = savedUri == null || Uri.parse(savedUri).scheme == "siphon"
    val sdMounted = viewModel.isSdCardMounted

    // Is the chosen folder really there and writable?
    val folderDoc = remember(savedUri, sdMounted) {
        if (savedUri != null && !usingPhoneStorage) {
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(savedUri))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    val folderReady = remember(folderDoc, sdMounted) {
        try {
            folderDoc != null && folderDoc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    val locationStatus = when {
        usingPhoneStorage -> "Phone storage (default)"
        folderReady -> "Folder selected: " + (folderDoc?.name ?: "custom folder")
        else -> "Folder unavailable, using phone storage"
    }
    val locationStatusColor = when {
        folderReady -> AccentSuccess
        usingPhoneStorage -> TextSecondary
        else -> AccentWarning
    }
    val locationDetail = when {
        usingPhoneStorage && sdMounted -> "Files are saved to Download/SIPHON. An SD card is available if you want to use it."
        usingPhoneStorage -> "Files are saved to Download/SIPHON. No SD card detected."
        folderReady -> "New downloads are saved to this folder."
        else -> "The chosen folder can't be written to right now, so files go to Download/SIPHON. Choose the folder again if you want to use it."
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
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(22.dp))

                // ---------------- Save location
                CollapsibleCard(
                    title = "Save location",
                    icon = when {
                        folderReady -> Icons.Default.CheckCircle
                        usingPhoneStorage -> Icons.Default.Folder
                        else -> Icons.Default.SdCard
                    },
                    iconTint = locationStatusColor.takeIf { folderReady || !usingPhoneStorage } ?: TextPrimary,
                    initiallyExpanded = true,
                    status = locationStatus,
                    statusColor = locationStatusColor
                ) {
                    Text(text = locationDetail, fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    SiphonSecondaryButton(
                        text = if (sdMounted) "Choose SD card or folder" else "Choose a folder",
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = TextPrimary) }
                    )

                    if (!usingPhoneStorage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SiphonSecondaryButton(
                            text = "Use phone storage",
                            onClick = { viewModel.setSdFolderUri(Uri.parse("siphon://phone")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------------- Background downloads
                CollapsibleCard(
                    title = "Background downloads",
                    icon = if (batteryUnrestricted) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                    iconTint = if (batteryUnrestricted) AccentSuccess else AccentWarning,
                    initiallyExpanded = false,
                    status = if (batteryUnrestricted) "On: SIPHON can run in the background" else "Off: downloads may stop when the screen is off",
                    statusColor = if (batteryUnrestricted) AccentSuccess else AccentWarning
                ) {
                    Text(
                        text = if (batteryUnrestricted) {
                            "Battery optimisation is off for SIPHON, so long and overnight downloads can finish and the waitlist can start by itself."
                        } else {
                            "Many phones stop background apps to save battery. Allow SIPHON to run in the background so long and overnight downloads can finish."
                        },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    if (!batteryUnrestricted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SiphonSecondaryButton(
                            text = "Allow background activity",
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------------- Network and waitlist
                CollapsibleCard(
                    title = "Network and waitlist",
                    icon = Icons.Default.Wifi,
                    iconTint = TextPrimary,
                    initiallyExpanded = false,
                    status = "${prefs.concurrencyLimit} at a time" + if (prefs.allowMeteredWifi) ", hotspots allowed" else "",
                    statusColor = TextSecondary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow metered Wi-Fi", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                            Text("Lets the waitlist start on phone hotspots", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = prefs.allowMeteredWifi,
                            onCheckedChange = { viewModel.setAllowMeteredWifi(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = PrimaryGradientStart,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkElevated
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Downloads at the same time: ${prefs.concurrencyLimit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = prefs.concurrencyLimit.toFloat(),
                        onValueChange = { viewModel.setConcurrency(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryGradientStart,
                            activeTrackColor = PrimaryGradientEnd,
                            inactiveTrackColor = DarkElevated
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---------------- Download engine
                CollapsibleCard(
                    title = "Download engine",
                    icon = Icons.Default.Refresh,
                    iconTint = TextPrimary,
                    initiallyExpanded = false,
                    status = if (engineVersion != null) "yt-dlp $engineVersion" else "yt-dlp",
                    statusColor = TextSecondary
                ) {
                    Text(
                        text = "SIPHON updates the engine by itself. If a site stops working, you can also update it manually.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    when (val state = engineState) {
                        is EngineUpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryGradientStart
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Updating engine...", fontSize = 13.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is EngineUpdateState.Success -> {
                            Text(text = state.message, fontSize = 12.sp, color = AccentSuccess)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is EngineUpdateState.Error -> {
                            Text(text = state.error, fontSize = 12.sp, color = AccentError)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is EngineUpdateState.Idle -> {}
                    }

                    SiphonSecondaryButton(
                        text = "Update engine",
                        onClick = { viewModel.updateEngine() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- App updates
                UpdateSection()

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- About
                CollapsibleCard(
                    title = "About",
                    icon = Icons.Default.Info,
                    iconTint = TextPrimary,
                    initiallyExpanded = false,
                    status = "SIPHON v${com.lonerxone.siphon.BuildConfig.VERSION_NAME}",
                    statusColor = TextSecondary
                ) {
                    Text(
                        text = "Credit: LONERXONE",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Build ${com.lonerxone.siphon.BuildConfig.VERSION_CODE}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "github.com/LONERXONE-ops/siphon",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A personal downloader. No accounts, no ads, no tracking.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    initiallyExpanded: Boolean,
    status: String?,
    statusColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron"
    )

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (status != null) {
                    Text(text = status, fontSize = 12.sp, color = statusColor)
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                content = content
            )
        }
    }
}