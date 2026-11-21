package com.lonerxone.siphon.data.storage

import android.net.Uri
import java.io.File

sealed interface StorageStatus {
    data class Ok(val stagingDir: File, val isRemovableSd: Boolean, val freeBytes: Long) : StorageStatus
    data class InsufficientSpace(val requiredBytes: Long, val freeBytes: Long, val isRemovableSd: Boolean) : StorageStatus
    object NoSdCardDetected : StorageStatus
    data class PermissionRequired(val uri: Uri?) : StorageStatus
    data class Error(val message: String) : StorageStatus
}

interface StorageManager {
    fun checkStorageStatus(requiredBytes: Long): StorageStatus
    fun getStagingDirectory(): File
    fun isSdCardMounted(): Boolean
    suspend fun saveToPhoneStorage(
        stagingFile: File,
        subfolderName: String? = null
    ): Result<Uri> = Result.failure(UnsupportedOperationException("Not supported"))

    suspend fun moveToTargetSaf(
        stagingFile: File,
        targetTreeUri: Uri,
        subfolderName: String? = null,
        onProgress: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Uri>
}
