package com.lonerxone.siphon.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter {
    ALL,
    VIDEO,
    AUDIO,
    FILES
}

enum class LibrarySort {
    DATE_DESC,
    SIZE_DESC,
    NAME_ASC
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter

    private val _selectedSort = MutableStateFlow(LibrarySort.DATE_DESC)
    val selectedSort: StateFlow<LibrarySort> = _selectedSort

    val completedDownloads: StateFlow<List<DownloadItem>> = combine(
        repository.allCompleted,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { items, query, filter, sort ->
        items
            .filter { item ->
                if (query.isBlank()) true
                else item.title.contains(query, ignoreCase = true) || item.site.contains(query, ignoreCase = true)
            }
            .filter { item ->
                when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.VIDEO -> item.formatLabel?.contains("audio", ignoreCase = true) == false
                    LibraryFilter.AUDIO -> item.formatLabel?.contains("audio", ignoreCase = true) == true
                    LibraryFilter.FILES -> item.engine.name == "ARIA2C"
                }
            }
            .sortedWith { a, b ->
                when (sort) {
                    LibrarySort.DATE_DESC -> (b.completedAt ?: 0L).compareTo(a.completedAt ?: 0L)
                    LibrarySort.SIZE_DESC -> b.bytesDownloaded.compareTo(a.bytesDownloaded)
                    LibrarySort.NAME_ASC -> a.title.compareTo(b.title, ignoreCase = true)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    fun setSort(sort: LibrarySort) {
        _selectedSort.value = sort
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteDownload(id, deleteFile = true)
        }
    }
}
