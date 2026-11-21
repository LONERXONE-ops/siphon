package com.lonerxone.siphon.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

    val savedUri = prefs.sdFolderUri
    val usingPhoneStorage = savedUri == null || Uri.parse(savedUri).scheme == "siphon"
    val sdMounted = viewModel.isSdCardMounted

    val locationTitle = if (usingPhoneStorage) "Phone storage" else "Custom folder"
    val locationDetail = when {
        usingPhoneStorage && sdMounted -> "Saving to Download/SIPHON. An SD card is available if you want to use it."
        usingPhoneStorage -> "Saving to Download/SIPHON. No SD card detected."
        sdMounted -> "Folder: ${Uri.parse(savedUri).lastPathSegment}"
        else -> "If this folder is on a missing SD card, files are saved to Download/SIPHON instead."
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

                SectionTitle("SAVE LOCATION")
                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (usingPhoneStorage) Icons.Default.Folder else Icons.Default.SdCard,
                            contentDescription = null,
                            tint = if (usingPhoneStorage) PrimaryGradientStart else AccentSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = locationTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = locationDetail,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

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

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle("NETWORK AND WAITLIST")
                SiphonCard(modifier = Modifier.fillMaxWidth()) {
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

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle("DOWNLOAD ENGINE")
                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "yt-dlp engine",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Update it when a site stops working. Sites change often and the engine needs to keep up.",
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

                UpdateSection()

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle("BACKGROUND DOWNLOADS")
                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = AccentWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Keep downloads running",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Many phones stop background apps to save battery. Allow SIPHON to run in the background so long and overnight downloads can finish.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SiphonSecondaryButton(
                        text = "Allow background activity",
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

                SectionTitle("ABOUT")
                SiphonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SIPHON v${com.lonerxone.siphon.BuildConfig.VERSION_NAME}",
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
}