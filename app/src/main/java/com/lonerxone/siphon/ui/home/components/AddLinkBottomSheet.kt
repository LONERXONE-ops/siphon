package com.lonerxone.siphon.ui.home.components

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
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
import com.lonerxone.siphon.ui.theme.TextMuted
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            when (uiState) {
                is AddSheetUiState.Loading -> LoadingContent()

                is AddSheetUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Couldn't read this link",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        SiphonSecondaryButton(
                            text = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is AddSheetUiState.MetadataLoaded -> {
                    MetadataContent(
                        metadata = uiState.metadata,
                        onDownloadNow = onDownloadNow,
                        onDownloadLater = onDownloadLater
                    )
                }

                is AddSheetUiState.MobileDataWarning -> {
                    Spacer(modifier = Modifier.height(120.dp))
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        title = {
                            Text(text = "You're on mobile data", fontWeight = FontWeight.Bold, color = TextPrimary)
                        },
                        text = {
                            Text(
                                text = "This download will use about ${uiState.sizeFormatted} of mobile data. Download anyway?",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            SiphonPrimaryButton(
                                text = "Download anyway",
                                onClick = { onConfirmMobileData(uiState.metadata, uiState.formatId) }
                            )
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { onDownloadLater(uiState.metadata, uiState.formatId, null) }
                            ) {
                                Text(text = "Wait for Wi-Fi", color = AccentWarning)
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
private fun LoadingContent() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.85f).height(16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = PrimaryGradientStart
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Inspecting link...",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = alpha * 0.09f))
    )
}

@Composable
private fun MetadataContent(
    metadata: MediaMetadata,
    onDownloadNow: (MediaMetadata, String?, String?) -> Unit,
    onDownloadLater: (MediaMetadata, String?, String?) -> Unit
) {
    var selectedId by remember(metadata.url) {
        mutableStateOf(metadata.formats.firstOrNull()?.formatId)
    }
    val selected = metadata.formats.firstOrNull { it.formatId == selectedId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(DarkElevated)
    ) {
        if (!metadata.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
        }

        if (metadata.formattedDuration.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = metadata.formattedDuration,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = metadata.title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = metadata.site,
        fontSize = 12.sp,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "Choose quality",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metadata.formats.forEachIndexed { index, format ->
            QualityRow(
                format = format,
                isSelected = format.formatId == selectedId,
                isBest = index == 0 && !format.isAudioOnly,
                onClick = { selectedId = format.formatId }
            )
        }
    }

    val sizeText = if (selected != null && selected.fileSizeApprox > 0L) selected.formattedSize else null
    if (sizeText != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Estimated size: $sizeText",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SiphonSecondaryButton(
            text = "Download later",
            onClick = { onDownloadLater(metadata, selected?.formatId, selected?.label) },
            modifier = Modifier.weight(1f)
        )
        SiphonPrimaryButton(
            text = "Download now",
            onClick = { onDownloadNow(metadata, selected?.formatId, selected?.label) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QualityRow(
    format: MediaFormat,
    isSelected: Boolean,
    isBest: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) DarkElevated else Color.Transparent)
            .border(1.dp, if (isSelected) PrimaryGradientStart else DarkBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) PrimaryGradientStart else Color.Transparent)
                    .border(1.5.dp, if (isSelected) PrimaryGradientStart else TextMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (isBest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryGradientStart.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BEST",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGradientStart
                            )
                        }
                    }
                }
                Text(
                    text = format.ext.uppercase(),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        Text(
            text = format.formattedSize,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}