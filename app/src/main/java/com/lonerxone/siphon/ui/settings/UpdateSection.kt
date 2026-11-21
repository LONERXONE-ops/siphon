package com.lonerxone.siphon.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.BuildConfig
import com.lonerxone.siphon.ui.components.SiphonCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.theme.AccentError
import com.lonerxone.siphon.ui.theme.AccentSuccess
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@Composable
fun UpdateSection(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var confirmedMobile by remember { mutableStateOf(false) }

    Text(
        text = "APP UPDATES",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    SiphonCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = PrimaryGradientStart,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "SIPHON v${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Build ${BuildConfig.VERSION_CODE}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (val s = state) {
            UpdateUiState.Idle, UpdateUiState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGradientStart
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Checking for updates...", fontSize = 13.sp, color = TextSecondary)
                }
            }

            is UpdateUiState.UpToDate -> {
                Text("You're on the latest version.", fontSize = 13.sp, color = AccentSuccess)
                Spacer(modifier = Modifier.height(12.dp))
                SiphonSecondaryButton(
                    text = "Check again",
                    onClick = { viewModel.checkForUpdate() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary) }
                )
            }

            is UpdateUiState.Available -> {
                val mb = s.sizeBytes / (1024.0 * 1024.0)
                Text(
                    text = "Update available: build ${s.buildNumber}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = String.format("%.1f MB download", mb),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                if (s.metered) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You're on a metered connection. Wi-Fi is recommended.",
                        fontSize = 12.sp,
                        color = AccentWarning
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SiphonPrimaryButton(
                    text = when {
                        s.metered && !confirmedMobile -> "Download (uses mobile data)"
                        s.metered && confirmedMobile -> "Tap again to confirm"
                        else -> "Download update"
                    },
                    onClick = {
                        if (s.metered && !confirmedMobile) {
                            confirmedMobile = true
                        } else {
                            viewModel.downloadUpdate()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateUiState.Downloading -> {
                Text(
                    text = "Downloading update... ${s.percent}%",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { s.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryGradientStart,
                    trackColor = DarkElevated
                )
            }

            is UpdateUiState.ReadyToInstall -> {
                Text("Update downloaded and ready.", fontSize = 13.sp, color = AccentSuccess)
                Spacer(modifier = Modifier.height(12.dp))
                SiphonPrimaryButton(
                    text = "Install update",
                    onClick = { viewModel.installUpdate(s.path) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateUiState.NeedsPermission -> {
                Text(
                    text = "Allow SIPHON to install apps on the screen that opened, then come back and tap Install.",
                    fontSize = 12.sp,
                    color = AccentWarning
                )
                Spacer(modifier = Modifier.height(12.dp))
                SiphonPrimaryButton(
                    text = "Install update",
                    onClick = { viewModel.installUpdate(s.path) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateUiState.Error -> {
                Text(text = s.message, fontSize = 12.sp, color = AccentError)
                Spacer(modifier = Modifier.height(8.dp))
                SiphonSecondaryButton(
                    text = "Open in browser",
                    onClick = { viewModel.openReleasePage() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SiphonSecondaryButton(
                    text = "Try again",
                    onClick = { viewModel.checkForUpdate() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}