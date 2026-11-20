package com.lonerxone.siphon.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageManager {

    /**
     * Determines whether a physical removable SD card is mounted.
     * We strictly verify via Environment.isExternalStorageRemovable() rather than array indexing.
     */
    private fun getRemovableSdCardDir(): File? {
        val dirs = context.getExternalFilesDirs(null) ?: return null
        return dirs.firstOrNull { dir ->
            dir != null &&
            Environment.isExternalStorageRemovable(dir) &&
            Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
        }
    }

    override fun isSdCardMounted(): Boolean {
        return getRemovableSdCardDir() != null
    }

    override fun getStagingDirectory(): File {
        val sdDir = getRemovableSdCardDir()
        val baseDir = sdDir ?: context.getExternalFilesDir(null) ?: context.filesDir
        val staging = File(baseDir, "staging")
        if (!staging.exists()) {
            staging.mkdirs()
        }
        return staging
    }

    override fun checkStorageStatus(requiredBytes: Long): StorageStatus {
        val sdDir = getRemovableSdCardDir()
        val isRemovable = sdDir != null
        val targetDir = sdDir ?: context.getExternalFilesDir(null) ?: context.filesDir

        val freeBytes = targetDir.usableSpace
        // Require at least 2x free space for staging file + final destination copy
        val requiredWithSafety = if (requiredBytes > 0) requiredBytes * 2 else 500 * 1024 * 1024L // minimum 500MB if unknown

        if (freeBytes < requiredWithSafety) {
            return StorageStatus.InsufficientSpace(
                requiredBytes = requiredWithSafety,
                freeBytes = freeBytes,
                isRemovableSd = isRemovable
            )
        }

        val staging = getStagingDirectory()
        return StorageStatus.Ok(
            stagingDir = staging,
            isRemovableSd = isRemovable,
            freeBytes = freeBytes
        )
    }

    override suspend fun moveToTargetSaf(
        stagingFile: File,
        targetTreeUri: Uri,
        subfolderName: String?,
        onProgress: ((copiedBytes: Long, totalBytes: Long) -> Unit)?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!stagingFile.exists()) {
            return@withContext Result.failure(IOException("Staging file does not exist: ${stagingFile.absolutePath}"))
        }

        val treeDoc = DocumentFile.fromTreeUri(context, targetTreeUri)
            ?: return@withContext Result.failure(IOException("Cannot access target SAF folder: $targetTreeUri"))

        if (!treeDoc.canWrite()) {
            return@withContext Result.failure(IOException("No write permission for target SAF folder: $targetTreeUri"))
        }

        val targetDir = if (!subfolderName.isNullOrBlank()) {
            treeDoc.findFile(subfolderName) ?: treeDoc.createDirectory(subfolderName)
                ?: treeDoc
        } else {
            treeDoc
        }

        val mimeType = getMimeType(stagingFile.name)
        val finalDoc = targetDir.createFile(mimeType, stagingFile.name)
            ?: return@withContext Result.failure(IOException("Failed to create destination file in SAF: ${stagingFile.name}"))

        val totalBytes = stagingFile.length()
        var copiedBytes = 0L

        try {
            val inputStream: InputStream = FileInputStream(stagingFile)
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(finalDoc.uri)

            if (outputStream == null) {
                finalDoc.delete()
                return@withContext Result.failure(IOException("Failed to open output stream for SAF URI: ${finalDoc.uri}"))
            }

            inputStream.use { inp ->
                outputStream.use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastReport = 0L
                    while (inp.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        copiedBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 200) {
                            onProgress?.invoke(copiedBytes, totalBytes)
                            lastReport = now
                        }
                    }
                    out.flush()
                }
            }

            // Strict byte-count integrity check
            if (copiedBytes != totalBytes) {
                finalDoc.delete()
                return@withContext Result.failure(
                    IOException("File size mismatch after copy! Staged: $totalBytes, Copied: $copiedBytes. Staging file preserved.")
                )
            }

            // Only delete staging file once destination byte count is verified
            stagingFile.delete()

            Result.success(finalDoc.uri)
        } catch (e: Exception) {
            finalDoc.delete()
            Result.failure(e)
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }
}
