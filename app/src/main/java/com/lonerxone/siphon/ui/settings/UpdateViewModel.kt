package com.lonerxone.siphon.ui.settings

import androidx.lifecycle.ViewModel
import com.lonerxone.siphon.data.update.UpdateManager
import com.lonerxone.siphon.data.update.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager
) : ViewModel() {
    val state: StateFlow<UpdateUiState> = manager.state

    fun check() = manager.check(autoDownload = false)
    fun download() = manager.download()
    fun install() = manager.install()
    fun openReleasePage() = manager.openReleasePage()
}