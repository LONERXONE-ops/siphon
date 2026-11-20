package com.lonerxone.siphon.ui.waitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.network.NetworkState
import com.lonerxone.siphon.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitlistSummary(
    val itemCount: Int = 0,
    val totalBytesApprox: Long = 0L
) {
    val formattedTotalSize: String
        get() {
            if (totalBytesApprox <= 0) return "calculating..."
            val mb = totalBytesApprox / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.0f MB", mb)
            }
        }
}

@HiltViewModel
class WaitlistViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val waitlistItems: StateFlow<List<DownloadItem>> = repository.waitlistDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadItem>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState())

    val summary: StateFlow<WaitlistSummary> = waitlistItems.map { items ->
        val totalBytes = items.sumOf { it.totalBytes }
        WaitlistSummary(itemCount = items.size, totalBytesApprox = totalBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaitlistSummary())

    fun pauseAll() {
        viewModelScope.launch { repository.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { repository.resumeAll() }
    }

    fun startNow(item: DownloadItem) {
        viewModelScope.launch {
            repository.resumeDownload(item.id)
        }
    }

    fun pauseItem(id: String) {
        viewModelScope.launch { repository.pauseDownload(id) }
    }

    fun resumeItem(id: String) {
        viewModelScope.launch { repository.resumeDownload(id) }
    }

    fun removeItem(id: String) {
        viewModelScope.launch { repository.cancelDownload(id) }
    }

    fun addBatchLinks(rawText: String) {
        val urls = rawText.lines().map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }
        if (urls.isNotEmpty()) {
            viewModelScope.launch {
                repository.addBatchLinks(urls)
            }
        }
    }
}
