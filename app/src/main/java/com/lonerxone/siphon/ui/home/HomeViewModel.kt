package com.lonerxone.siphon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.engine.model.MediaMetadata
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val activeDownloads: StateFlow<List<DownloadItem>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentCompleted: StateFlow<List<DownloadItem>> = repository.recentCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState())

    private val _addSheetState = MutableStateFlow<AddSheetUiState>(AddSheetUiState.Idle)
    val addSheetState: StateFlow<AddSheetUiState> = _addSheetState.asStateFlow()

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

            // If on mobile data and not auto-allowed, prompt user (PRD 5.2)
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

    fun confirmMobileDataDownload(metadata: MediaMetadata, formatId: String?) {
        val format = metadata.formats.find { it.formatId == formatId } ?: metadata.formats.firstOrNull()
        startDownloadNow(metadata, formatId, format?.label, mobileApproved = true)
    }

    fun downloadLater(metadata: MediaMetadata, formatId: String?, formatLabel: String?) {
        viewModelScope.launch {
            repository.downloadLater(
                url = metadata.url,
                metadata = metadata,
                formatId = formatId,
                formatLabel = formatLabel
            )
            _addSheetState.value = AddSheetUiState.Idle
        }
    }

    private fun startDownloadNow(
        metadata: MediaMetadata,
        formatId: String?,
        formatLabel: String?,
        mobileApproved: Boolean
    ) {
        viewModelScope.launch {
            repository.downloadNow(
                url = metadata.url,
                metadata = metadata,
                formatId = formatId,
                formatLabel = formatLabel,
                mobileDataApproved = mobileApproved
            )
            _addSheetState.value = AddSheetUiState.Idle
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

    fun dismissSheet() {
        _addSheetState.value = AddSheetUiState.Idle
    }
}
