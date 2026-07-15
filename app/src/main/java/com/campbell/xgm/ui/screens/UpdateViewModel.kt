package com.campbell.xgm.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campbell.xgm.util.ReleaseInfo
import com.campbell.xgm.util.UpdateManager
import com.campbell.xgm.util.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(val release: ReleaseInfo) : UpdateUiState()
    data class Downloading(val downloaded: Long, val total: Long) : UpdateUiState()
    data class Ready(val file: File, val release: ReleaseInfo) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    val currentVersion: String = UpdateManager.currentVersion(application)

    fun skippedVersion(): String = prefs.getString("skipped_update_version", "") ?: ""

    fun checkForUpdate(autoPrompt: Boolean = false) {
        if (_state.value is UpdateUiState.Checking || _state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = UpdateManager.checkForUpdate(getApplication())) {
                is UpdateResult.Available -> {
                    if (autoPrompt && result.release.tag == skippedVersion()) {
                        _state.value = UpdateUiState.UpToDate
                    } else {
                        _state.value = UpdateUiState.Available(result.release)
                    }
                }
                is UpdateResult.UpToDate -> _state.value = UpdateUiState.UpToDate
                is UpdateResult.Error -> _state.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        _state.value = UpdateUiState.Downloading(0, release.apkSize)
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(UpdateManager.updatesDir(getApplication()), "campbellxgm-update.apk")
            file.delete()
            val ok = UpdateManager.downloadApk(release.apkUrl ?: "", file) { downloaded, total ->
                _state.value = UpdateUiState.Downloading(downloaded, total)
            }
            if (ok && file.exists() && file.length() > 0) {
                _state.value = UpdateUiState.Ready(file, release)
            } else {
                _state.value = UpdateUiState.Error("Download failed. Check your connection and try again.")
            }
        }
    }

    fun install(file: File) {
        UpdateManager.installApk(getApplication(), file)
    }

    fun skip(version: String) {
        prefs.edit().putString("skipped_update_version", version).apply()
    }

    fun reset() {
        _state.value = UpdateUiState.Idle
    }
}
