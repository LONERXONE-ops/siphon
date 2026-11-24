package com.lonerxone.siphon.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonerxone.siphon.data.datastore.UserPreferences
import com.lonerxone.siphon.data.datastore.UserPreferencesRepository
import com.lonerxone.siphon.data.repository.DownloadRepository
import com.lonerxone.siphon.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EngineUpdateState {
    object Idle : EngineUpdateState
    object Checking : EngineUpdateState
    data class Success(val message: String) : EngineUpdateState
    data class Error(val error: String) : EngineUpdateState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val repository: DownloadRepository,
    private val storageManager: StorageManager
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    /** False until the saved settings have really been read, so the screen never shows placeholder values. */
    val preferencesLoaded: StateFlow<Boolean> = preferencesRepository.userPreferencesFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _engineUpdateState = MutableStateFlow<EngineUpdateState>(EngineUpdateState.Idle)
    val engineUpdateState: StateFlow<EngineUpdateState> = _engineUpdateState.asStateFlow()

    val isSdCardMounted: Boolean
        get() = storageManager.isSdCardMounted()

    fun setSdFolderUri(uri: Uri) {
        viewModelScope.launch {
            preferencesRepository.setSdFolderUri(uri.toString())
        }
    }

    fun setConcurrency(limit: Int) {
        viewModelScope.launch {
            preferencesRepository.setConcurrencyLimit(limit)
        }
    }

    fun setAllowMeteredWifi(allow: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAllowMeteredWifi(allow)
        }
    }

    fun setAutoAllowMobileMb(mb: Long?) {
        viewModelScope.launch {
            preferencesRepository.setAutoAllowMobileMb(mb)
        }
    }

    fun setSpeedLimit(kbps: Long) {
        viewModelScope.launch {
            preferencesRepository.setSpeedLimit(kbps)
        }
    }

    fun updateEngine() {
        _engineUpdateState.value = EngineUpdateState.Checking
        viewModelScope.launch {
            repository.updateEngine().fold(
                onSuccess = { msg ->
                    _engineUpdateState.value = EngineUpdateState.Success(msg)
                },
                onFailure = { err ->
                    _engineUpdateState.value = EngineUpdateState.Error(err.message ?: "Update failed")
                }
            )
        }
    }

    fun clearEngineState() {
        _engineUpdateState.value = EngineUpdateState.Idle
    }
}
