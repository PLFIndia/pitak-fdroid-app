package dev.khoj.pitaka.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.vault.VaultPreferences
import dev.khoj.pitaka.domain.usecase.CreateBackupArchiveUseCase
import dev.khoj.pitaka.domain.usecase.RestoreBackupArchiveUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val app: Application,
    private val createBackup: CreateBackupArchiveUseCase,
    private val restoreBackup: RestoreBackupArchiveUseCase,
    private val prefs: VaultPreferences,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(BackupUiState(
        lastBackupAtMs = prefs.lastBackupAtMs(),
        stalenessThresholdMs = prefs.backupStalenessThresholdMs(),
    ))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun refresh() {
        _state.value = _state.value.copy(
            lastBackupAtMs = prefs.lastBackupAtMs(),
            stalenessThresholdMs = prefs.backupStalenessThresholdMs(),
        )
    }

    fun onBackupDestinationPicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val r = runCatching {
                app.contentResolver.openOutputStream(uri, "w")?.use { out ->
                    createBackup(out)
                }
            }.getOrNull()
            _state.value = _state.value.copy(busy = false)
            when (r) {
                is CreateBackupArchiveUseCase.Result.Success -> {
                    refresh()
                    _events.trySend(BackupEvent.BackupSaved)
                }
                is CreateBackupArchiveUseCase.Result.Failed ->
                    _events.trySend(BackupEvent.Error(r.reason))
                null -> _events.trySend(BackupEvent.Error(app.getString(R.string.backup_error_cant_open_destination)))
            }
        }
    }

    fun onRestoreSourcePicked(uri: Uri) {
        // Save the URI; we need a passphrase first.
        _state.value = _state.value.copy(pendingRestoreUri = uri)
    }

    fun onRestoreWithPassphrase(passphrase: CharArray) {
        val uri = _state.value.pendingRestoreUri ?: run {
            java.util.Arrays.fill(passphrase, '\u0000'); return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    when (val r = restoreBackup(input, passphrase)) {
                        is RestoreBackupArchiveUseCase.Result.Success ->
                            _events.trySend(BackupEvent.RestoreSucceeded)
                        RestoreBackupArchiveUseCase.Result.WrongPassphrase ->
                            _events.trySend(BackupEvent.WrongPassphrase)
                        RestoreBackupArchiveUseCase.Result.MissingBackupBlob ->
                            _events.trySend(BackupEvent.Error(app.getString(R.string.backup_error_no_blob)))
                        is RestoreBackupArchiveUseCase.Result.SchemaTooNew ->
                            _events.trySend(BackupEvent.Error(app.getString(R.string.backup_error_schema_too_new, r.schemaVersion)))
                        is RestoreBackupArchiveUseCase.Result.Failed ->
                            _events.trySend(BackupEvent.Error(r.reason))
                    }
                }
            } finally {
                _state.value = _state.value.copy(busy = false, pendingRestoreUri = null)
                java.util.Arrays.fill(passphrase, '\u0000')
            }
        }
    }

    fun onCancelRestore() {
        _state.value = _state.value.copy(pendingRestoreUri = null)
    }
}

data class BackupUiState(
    val lastBackupAtMs: Long = 0L,
    val stalenessThresholdMs: Long,
    val busy: Boolean = false,
    val pendingRestoreUri: Uri? = null,
)

sealed interface BackupEvent {
    data object BackupSaved : BackupEvent
    data object RestoreSucceeded : BackupEvent
    data object WrongPassphrase : BackupEvent
    data class Error(val message: String) : BackupEvent
}
