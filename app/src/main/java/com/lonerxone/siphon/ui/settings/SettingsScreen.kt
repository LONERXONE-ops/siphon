package com.lonerxone.siphon.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
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

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val engineState by viewModel.engineUpdateState.collectAsState()
    val context = LocalContext.current

    // SAF folder picker launcher
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
                    text = "SETTINGS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))

                // SD Card Storage Section
                Text(
                    text = "STORAGE (SD CARD FIRST)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SdCard,
                            contentDescription = null,
                            tint = if (viewModel.isSdCardMounted) AccentSuccess else AccentWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (viewModel.isSdCardMounted) "Physical SD card detected" else "No physical SD card found",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (prefs.sdFolderUri != null) "Folder configured: ${Uri.parse(prefs.sdFolderUri).lastPathSegment}" else "Folder not configured",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SiphonSecondaryButton(
                        text = if (prefs.sdFolderUri != null) "Change SD Folder" else "Select SD Folder",
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = TextPrimary) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Network & Mobile Data Rules
                Text(
                    text = "NETWORK & WAITLIST RULES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    // Metered Wi-Fi Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow metered Wi-Fi", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                            Text("Permits auto-start on phone hotspots", fontSize = 12.sp, color = TextSecondary)
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

                    // Concurrency Limit
                    Text(
                        text = "Max Concurrent Downloads: ${prefs.concurrencyLimit}",
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

                Spacer(modifier = Modifier.height(24.dp))

                // Engine Management Section
                Text(
                    text = "DOWNLOAD ENGINES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "yt-dlp & aria2c Engines",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Update the engine when sites change their video extraction format.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    when (engineState) {
                        is EngineUpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryGradientStart)
                                Spacer(modifier = Modifier.padding(6.dp))
                                Text("Updating engine from release feed...", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        is EngineUpdateState.Success -> {
                            Text(
                                text = (engineState as EngineUpdateState.Success).message,
                                fontSize = 12.sp,
                                color = AccentSuccess
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is EngineUpdateState.Error -> {
                            Text(
                                text = (engineState as EngineUpdateState.Error).error,
                                fontSize = 12.sp,
                                color = AccentError
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is EngineUpdateState.Idle -> {}
                    }

                    SiphonSecondaryButton(
                        text = "Update yt-dlp Engine",
                        onClick = { viewModel.updateEngine() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Samsung Galaxy A06 Battery Guide
                Text(
                    text = "BATTERY & BACKGROUND RESILIENCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = AccentWarning)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = "Samsung One UI / Low-End Hardware",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Samsung Galaxy A06 aggressively terminates background services. To ensure overnight downloads complete, whitelist SIPHON:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SiphonSecondaryButton(
                        text = "Ignore Battery Optimization",
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // About Screen (PRD Header requirement)
                Text(
                    text = "ABOUT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SIPHON v2.0.0",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Credit: LONERXONE",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = PrimaryGradientStart
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Repository: github.com/LONERXONE-ops/siphon",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sideloaded personal downloader. Single user, zero analytics, zero ads, zero accounts.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
