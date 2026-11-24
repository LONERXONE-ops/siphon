package com.lonerxone.siphon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.engine.RequestHeaderRegistry
import com.lonerxone.siphon.data.engine.model.MediaFormat
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.data.repository.DownloadRepository
import com.lonerxone.siphon.data.update.UpdateManager
import com.lonerxone.siphon.data.update.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddSheetUiState {
    object Idle : AddSheetUiState
    object Loading : AddSheetUiState
    data class MetadataLoaded(val metadata: MediaMetadata, val isMobileData: Boolean) : AddSheetUiState
    data class MobileDataWarning(val metadata: MediaMetadata, val formatId: String?, val sizeFormatted: String) : AddSheetUiState
    data class Error(val message: String) : AddSheetUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
    private val preferencesRepository: UserPreferencesRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    val activeDownloads: StateFlow<List<DownloadItem>> = combine(
        repository.activeDownloads,
        repository.waitlistDownloads
    ) { active, waiting ->
        val startedPaused = waiting.filter {
            it.progress > 0f && (
                it.status == DownloadStatus.PAUSED_BY_USER ||
                    it.status == DownloadStatus.PAUSED_NO_WIFI ||
                    it.status == DownloadStatus.PAUSED_NO_STORAGE
                )
        }
        (active + startedPaused).distinctBy { it.id }.sortedBy { it.position }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedDownloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .map { list -> list.filter { it.status == DownloadStatus.FAILED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentCompleted: StateFlow<List<DownloadItem>> = repository.recentCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryStats: StateFlow<Pair<Int, Long>> = repository.allCompleted
        .map { list -> Pair(list.size, list.sumOf { maxOf(it.totalBytes, it.bytesDownloaded) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0L))

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState())

    private val _addSheetState = MutableStateFlow<AddSheetUiState>(AddSheetUiState.Idle)
    val addSheetState: StateFlow<AddSheetUiState> = _addSheetState.asStateFlow()

    private val _engineUpdating = MutableStateFlow(false)
    val engineUpdating: StateFlow<Boolean> = _engineUpdating.asStateFlow()

    val updateState: StateFlow<UpdateUiState> = updateManager.state

    fun fetchLinkInfo(url: String) {
        if (url.isBlank()) return
        _addSheetState.value = AddSheetUiState.Loading

        viewModelScope.launch {
            repository.fetchMetadata(url).fold(
                onSuccess = { metadata ->
                    val isMobile = !networkMonitor.getCurrentState().isWifi
                    _addSheetState.value = AddSheetUiState.MetadataLoaded(metadata, isMobile)
                },
                onFailure = { error ->
                    _addSheetState.value = AddSheetUiState.Error(error.message ?: "Failed to fetch link info")
                }
            )
        }
    }

    fun onDownloadNowClicked(metadata: MediaMetadata, formatId: String?, formatLabel: String?) {
        viewModelScope.launch {
            val netState = networkMonitor.getCurrentState()
            val format = metadata.formats.find { it.formatId == formatId } ?: metadata.formats.firstOrNull()
            val size = format?.fileSizeApprox ?: 0L

            val prefs = preferencesRepository.userPreferencesFlow.first()
            val autoAllowSize = prefs.autoAllowMobileDataMb?.let { it * 1024 * 1024 }

            if (!netState.isWifi && (autoAllowSize == null || size > autoAllowSize)) {
                _addSheetState.value = AddSheetUiState.MobileDataWarning(
                    metadata = metadata,
                    formatId = formatId,
                    sizeFormatted = format?.formattedSize ?: "unknown size"
                )
            } else {
                startDownloadNow(metadata, formatId, formatLabel, mobileApproved = !netState.isWifi)
            }
        }
    }

    /** Images and videos picked from a page scan. [later] puts them on the waitlist. */
    fun onDownloadSelected(metadata: MediaMetadata, ids: List<String>, later: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (later) {
                queueSelected(metadata, ids, later = true, mobileApproved = false)
                return@launch
            }
            val net = networkMonitor.getCurrentState()
            if (!net.isWifi) {
                _addSheetState.value = AddSheetUiState.MobileDataWarning(
                    metadata = metadata,
                    formatId = ids.joinToString("\n"),
                    sizeFormatted = "${ids.size} file(s)"
                )
            } else {
                queueSelected(metadata, ids, later = false, mobileApproved = false)
            }
        }
    }

    fun confirmMobileDataDownload(metadata: MediaMetadata, formatId: String?) {
        if (isSelectionId(formatId)) {
            viewModelScope.launch {
                queueSelected(metadata, formatId!!.split("\n"), later = false, mobileApproved = true)
            }
            return
        }
        val format = metadata.formats.find { it.formatId == formatId } ?: metadata.formats.firstOrNull()
        startDownloadNow(metadata, formatId, format?.label, mobileApproved = true)
    }

    fun downloadLater(metadata: MediaMetadata, formatId: String?, formatLabel: String?) {
        if (isSelectionId(formatId)) {
            viewModelScope.launch {
                queueSelected(metadata, formatId!!.split("\n"), later = true, mobileApproved = false)
            }
            return
        }
        viewModelScope.launch {
            try {
                repository.downloadLater(
                    url = metadata.url,
                    metadata = metadata,
                    formatId = formatId,
                    formatLabel = formatLabel
                )
                _addSheetState.value = AddSheetUiState.Idle
            } catch (e: Exception) {
                _addSheetState.value = AddSheetUiState.Error(e.message ?: "Could not add to the waitlist")
            }
        }
    }

    private fun isSelectionId(id: String?): Boolean {
        return id != null && (id.startsWith("img:") || id.startsWith("vid:"))
    }

    private suspend fun queueSelected(
        metadata: MediaMetadata,
        ids: List<String>,
        later: Boolean,
        mobileApproved: Boolean
    ) {
        var failure: String? = null
        ids.forEachIndexed { index, id ->
            val url = id.substringAfter(":")
            val isImage = id.startsWith("img:")
            val ext = extensionOf(url, if (isImage) "jpg" else "mp4")
            val label = if (isImage) "Image ($ext)" else "Video ($ext)"

            RequestHeaderRegistry.put(url, mapOf("Referer" to metadata.url), true)

            val item = MediaMetadata(
                url = url,
                title = metadata.title.take(70) + if (ids.size > 1) " (${index + 1})" else "",
                thumbnailUrl = if (isImage) url else metadata.thumbnailUrl,
                durationSeconds = 0L,
                site = metadata.site,
                formats = listOf(MediaFormat(formatId = "direct", label = label, ext = ext, fileSizeApprox = 0L)),
                isDirectFile = true
            )

            val result = if (later) {
                repository.downloadLater(url = url, metadata = item, formatId = "direct", formatLabel = label)
            } else {
                repository.downloadNow(
                    url = url,
                    metadata = item,
                    formatId = "direct",
                    formatLabel = label,
                    mobileDataApproved = mobileApproved
                )
            }
            if (result.isFailure && failure == null) {
                failure = result.exceptionOrNull()?.message ?: "Could not start a download"
            }
        }
        _addSheetState.value = if (failure == null) AddSheetUiState.Idle else AddSheetUiState.Error(failure!!)
    }

    private fun extensionOf(url: String, fallback: String): String {
        val path = url.substringBefore("?").lowercase()
        val ext = path.substringAfterLast('.', "")
        val known = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "mp4", "webm", "m4v", "mov")
        if (ext in known) return ext
        val format = Regex("""format=([a-z0-9]+)""").find(url.lowercase())?.groupValues?.get(1)
        return if (format != null && format in known) format else fallback
    }

    private fun startDownloadNow(
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        mobileApproved: Boolean
    ) {
        viewModelScope.launch {
            try {
                val result = repository.downloadNow(
                    url = metadata.url,
                    metadata = metadata,
                    formatId = formatId,
                    formatLabel = formatLabel,
                    mobileDataApproved = mobileApproved
                )
                if (result.isSuccess) {
                    _addSheetState.value = AddSheetUiState.Idle
                } else {
                    _addSheetState.value = AddSheetUiState.Error(
                        result.exceptionOrNull()?.message ?: "Could not start the download"
                    )
                }
            } catch (e: Exception) {
                _addSheetState.value = AddSheetUiState.Error(e.message ?: "Could not start the download")
            }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { repository.pauseDownload(id) }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { repository.resumeDownload(id) }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { repository.cancelDownload(id) }
    }

    fun retryDownload(id: String) {
        viewModelScope.launch { repository.resumeDownload(id) }
    }

    fun updateEngine() {
        viewModelScope.launch {
            _engineUpdating.value = true
            try {
                repository.updateEngine()
            } finally {
                _engineUpdating.value = false
            }
        }
    }

    fun downloadUpdate() {
        updateManager.download()
    }

    fun installUpdate() {
        updateManager.install()
    }

    fun dismissSheet() {
        _addSheetState.value = AddSheetUiState.Idle
    }
}