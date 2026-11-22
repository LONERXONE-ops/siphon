package com.lonerxone.siphon.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.BuildConfig
import com.lonerxone.siphon.data.update.UpdateUiState
import com.lonerxone.siphon.ui.components.ExpandableCard
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.components.formatFileSize
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

    val status = when (val s = state) {
        UpdateUiState.Idle, UpdateUiState.Checking -> "Checking for updates..."
        is UpdateUiState.UpToDate -> "You're on the latest version"
        is UpdateUiState.Available -> "Update available: build ${s.buildNumber}"
        is UpdateUiState.Downloading -> "Downloading update... ${s.percent}%"
        is UpdateUiState.ReadyToInstall -> "Update ready to install"
        is UpdateUiState.NeedsPermission -> "Allow installs to continue"
        is UpdateUiState.Error -> "Couldn't check for updates"
    }
    val statusColor = when (state) {
        is UpdateUiState.UpToDate, is UpdateUiState.ReadyToInstall -> AccentSuccess
        is UpdateUiState.Available, is UpdateUiState.NeedsPermission -> AccentWarning
        is UpdateUiState.Error -> AccentError
        else -> TextSecondary
    }
    val icon = when (state) {
        is UpdateUiState.UpToDate -> Icons.Default.CheckCircle
        else -> Icons.Default.SystemUpdate
    }
    val iconTint = when (state) {
        is UpdateUiState.UpToDate -> AccentSuccess
        else -> TextPrimary
    }

    ExpandableCard(
        title = "App updates",
        icon = icon,
        iconTint = iconTint,
        initiallyExpanded = false,
        status = status,
        statusColor = statusColor
    ) {
        Text(
            text = "SIPHON v${BuildConfig.VERSION_NAME}  •  Build ${BuildConfig.VERSION_CODE}",
            fontSize = 12.sp,
            color = TextSecondary
        )
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
                SiphonSecondaryButton(
                    text = "Check again",
                    onClick = { viewModel.check() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary) }
                )
            }

            is UpdateUiState.Available -> {
                Text(
                    text = "${formatFileSize(s.sizeBytes)} download",
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
                            viewModel.download()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateUiState.Downloading -> {
                LinearProgressIndicator(
                    progress = { s.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryGradientStart,
                    trackColor = DarkElevated
                )
            }

            is UpdateUiState.ReadyToInstall -> {
                Text("The update is downloaded and ready.", fontSize = 12.sp, color = AccentSuccess)
                Spacer(modifier = Modifier.height(12.dp))
                SiphonPrimaryButton(
                    text = "Install update",
                    onClick = { viewModel.install() },
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
                    onClick = { viewModel.install() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateUiState.Error -> {
                Text(text = s.message, fontSize = 12.sp, color = AccentError)
                Spacer(modifier = Modifier.height(12.dp))
                SiphonSecondaryButton(
                    text = "Try again",
                    onClick = { viewModel.check() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SiphonSecondaryButton(
                    text = "Open in browser",
                    onClick = { viewModel.openReleasePage() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}