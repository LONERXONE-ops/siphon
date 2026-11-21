package com.lonerxone.siphon.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val frameCache = LruCache<String, ImageBitmap>(48)

/**
 * Shows a real frame from the downloaded video when the file exists,
 * otherwise the site's preview image, otherwise a clean placeholder.
 */
@Composable
fun MediaThumbnail(item: DownloadItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = item.finalUri
    var frame by remember(key) {
        mutableStateOf<ImageBitmap?>(if (key != null) frameCache.get(key) else null)
    }

    LaunchedEffect(key) {
        if (key != null && frame == null) {
            val loaded = withContext(Dispatchers.IO) { extractFrame(context, key) }
            if (loaded != null) {
                frameCache.put(key, loaded)
                frame = loaded
            }
        }
    }

    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(DarkElevated, DarkSurface))),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = frame
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!item.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = placeholderIcon(item),
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun placeholderIcon(item: DownloadItem): ImageVector {
    val name = item.title.lowercase()
    return when {
        name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".opus") || name.endsWith(".flac") -> Icons.Default.MusicNote
        name.endsWith(".pdf") || name.endsWith(".zip") || name.endsWith(".apk") -> Icons.Default.InsertDriveFile
        else -> Icons.Default.PlayArrow
    }
}

private fun extractFrame(context: Context, uriString: String): ImageBitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(uriString))
        val raw = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        val maxSide = 480
        val scale = maxSide.toFloat() / maxOf(raw.width, raw.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
        } else {
            raw
        }
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
        }
    }
}