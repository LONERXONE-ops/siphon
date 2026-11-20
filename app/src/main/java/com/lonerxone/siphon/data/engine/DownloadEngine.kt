package com.lonerxone.siphon.data.engine

import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.engine.model.DownloadProgress
import com.lonerxone.siphon.data.engine.model.DownloadRequest
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import java.io.File

interface DownloadEngine {
    val engineType: EngineType

    suspend fun fetchMetadata(url: String): Result<MediaMetadata>

    suspend fun startDownload(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File>

    suspend fun pause(id: String): Boolean

    suspend fun resume(id: String): Boolean

    suspend fun cancel(id: String): Boolean
}
