package com.anireku.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anireku.app.BuildConfig
import com.anireku.app.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val checking: Boolean = false,
    val available: UpdateChecker.Available? = null,
    val downloading: Boolean = false,
    val readyToInstall: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = UpdateChecker(application)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdates() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }

        viewModelScope.launch {
            runCatching { checker.check() }
                .onSuccess { found ->
                    _state.update {
                        it.copy(
                            checking = false,
                            available = found,
                            message = if (found == null) "Kamu sudah pakai versi terbaru." else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            checking = false,
                            message = "Gagal cek update: ${error.message ?: "tidak diketahui"}",
                        )
                    }
                }
        }
    }

    fun download() {
        val update = _state.value.available ?: return
        if (_state.value.downloading) return

        val id = runCatching { checker.download(update) }
            .getOrElse { error ->
                _state.update { it.copy(message = "Gagal mulai unduhan: ${error.message}") }
                return
            }

        _state.update { it.copy(downloading = true) }
        checker.observeDownload(
            downloadId = id,
            onReady = {
                _state.update { it.copy(downloading = false, readyToInstall = true) }
            },
            onFailed = { reason ->
                _state.update { it.copy(downloading = false, message = reason) }
            },
        )
    }

    fun install() {
        val version = _state.value.available?.version ?: return
        runCatching { checker.install(version) }
            .onFailure { error ->
                _state.update { it.copy(message = "Gagal membuka installer: ${error.message}") }
            }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
