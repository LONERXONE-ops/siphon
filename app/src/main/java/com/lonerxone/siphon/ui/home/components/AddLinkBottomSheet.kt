package com.lonerxone.siphon.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.ui.components.SiphonPrimaryButton
import com.lonerxone.siphon.ui.components.SiphonSecondaryButton
import com.lonerxone.siphon.ui.home.AddSheetUiState
import com.lonerxone.siphon.ui.theme.AccentWarning
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkBottomSheet(
    uiState: AddSheetUiState,
    onDismiss: () -> Unit,
    onDownloadNow: (MediaMetadata, String?, String?) -> Unit,
    onDownloadLater: (MediaMetadata, String?, String?) -> Unit,
    onConfirmMobileData: (MediaMetadata, String?) -> Unit
) {
    if (uiState is AddSheetUiState.Idle) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), // 28dp per PRD
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            when (uiState) {
                is AddSheetUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryGradientStart)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fetching link info...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                is AddSheetUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Failed to load link",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SiphonSecondaryButton(text = "Close", onClick = onDismiss)
                    }
                }
                is AddSheetUiState.MetadataLoaded -> {
                    val metadata = uiState.metadata
                    var selectedFormat by remember {
                        mutableStateOf(metadata.formats.firstOrNull())
                    }

                    // Thumbnail + Title header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = metadata.thumbnailUrl ?: android.R.drawable.stat_sys_download,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = metadata.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${metadata.site} • ${metadata.formattedDuration}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SELECT QUALITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Format Picker List
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(metadata.formats) { format ->
                            val isSelected = format.formatId == selectedFormat?.formatId
                            FormatOptionRow(
                                format = format,
                                isSelected = isSelected,
                                onClick = { selectedFormat = format }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons: Download Now vs Download Later
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SiphonSecondaryButton(
                            text = "Download later",
                            onClick = {
                                onDownloadLater(metadata, selectedFormat?.formatId, selectedFormat?.label)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SiphonPrimaryButton(
                            text = "Download now",
                            onClick = {
                                onDownloadNow(metadata, selectedFormat?.formatId, selectedFormat?.label)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is AddSheetUiState.MobileDataWarning -> {
                    // Mobile Data Confirmation Dialog (PRD 5.2)
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        title = {
                            Text(text = "Mobile Data Warning", fontWeight = FontWeight.Bold, color = TextPrimary)
                        },
                        text = {
                            Text(
                                text = "This download will use approximately ${uiState.sizeFormatted} of mobile data. Download anyway?",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            SiphonPrimaryButton(
                                text = "Download anyway",
                                onClick = {
                                    onConfirmMobileData(uiState.metadata, uiState.formatId)
                                }
                            )
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    onDownloadLater(uiState.metadata, uiState.formatId, null)
                                }
                            ) {
                                Text(text = "Download later", color = AccentWarning)
                            }
                        },
                        containerColor = DarkElevated
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun FormatOptionRow(
    format: MediaFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DarkElevated else DarkSurface)
            .border(1.dp, if (isSelected) PrimaryGradientStart else DarkBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PrimaryGradientStart,
                    unselectedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = format.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = TextPrimary
            )
        }

        Text(
            text = format.formattedSize,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}
