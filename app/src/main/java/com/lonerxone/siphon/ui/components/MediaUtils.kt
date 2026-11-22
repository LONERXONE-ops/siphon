package com.lonerxone.siphon.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.lonerxone.siphon.ui.player.PlayerActivity

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

/** VIDEO, AUDIO, IMAGE or FILE, guessed from the format label and the file name. */
fun mediaKindOf(label: String?, name: String): String {
    val l = (label ?: "").lowercase()
    val n = name.lowercase()
    val imageExts = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic")
    val audioExts = listOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    val videoExts = listOf("mp4", "m4v", "webm", "mkv", "mov", "avi")
    return when {
        l.contains("audio") || audioExts.any { l.contains("($it)") || n.endsWith(".$it") } -> "AUDIO"
        l.contains("image") || imageExts.any { l.contains("($it)") || n.endsWith(".$it") } -> "IMAGE"
        l.startsWith("file (") || l.startsWith("direct file") ->
            if (videoExts.any { l.contains("($it)") || n.endsWith(".$it") }) "VIDEO" else "FILE"
        else -> "VIDEO"
    }
}

fun formatTransferSpeed(bytesPerSecond: Long): String {
    return if (bytesPerSecond <= 0L) "" else formatFileSize(bytesPerSecond) + "/s"
}

fun formatTimeLeft(seconds: Long): String {
    if (seconds <= 0L) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m left"
        m > 0 -> "${m}m ${s}s left"
        else -> "${s}s left"
    }
}

fun describeSaveLocation(finalUri: String?): String {
    if (finalUri.isNullOrBlank()) return "App storage"
    return if (finalUri.contains("/media/") || finalUri.contains("primary")) "Phone storage" else "SD card"
}

fun guessMimeType(context: Context, uriString: String, name: String): String {
    val fromResolver = try {
        context.contentResolver.getType(Uri.parse(uriString))
    } catch (e: Exception) {
        null
    }
    if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") return fromResolver
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

/** Videos and audio open in the built-in player, everything else asks Android. */
fun openMedia(context: Context, uriString: String, name: String) {
    val mime = guessMimeType(context, uriString, name)

    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
        try {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                data = Uri.parse(uriString)
                putExtra(PlayerActivity.EXTRA_TITLE, name)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // fall through to the system chooser
        }
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uriString), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

fun shareMedia(context: Context, uriString: String, name: String) {
    val mime = guessMimeType(context, uriString, name)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
    }
}