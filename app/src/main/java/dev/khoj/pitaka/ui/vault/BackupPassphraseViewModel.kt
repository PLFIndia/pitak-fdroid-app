package dev.khoj.pitaka.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.crypto.BackupPassphraseWrapper
import dev.khoj.pitaka.domain.usecase.SetBackupPassphraseUseCase
import dev.khoj.pitaka.domain.usecase.WipeVaultAndStartFreshUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupPassphraseViewModel @Inject constructor(
    private val setBackup: SetBackupPassphraseUseCase,
    private val backupWrapper: BackupPassphraseWrapper,
    private val wipeVault: WipeVaultAndStartFreshUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _events = Channel<BackupPassphraseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun hasBackup(): Boolean = backupWrapper.isSet()
    fun hint(): String? = backupWrapper.getHint()

    fun onSave(passphrase: CharArray, hint: String?) {
        viewModelScope.launch {
            when (val r = setBackup(passphrase, hint?.takeIf { it.isNotBlank() })) {
                SetBackupPassphraseUseCase.Result.Success -> _events.trySend(BackupPassphraseEvent.Saved)
                SetBackupPassphraseUseCase.Result.PassphraseTooShort -> _events.trySend(BackupPassphraseEvent.TooShort)
                SetBackupPassphraseUseCase.Result.VaultLocked -> _events.trySend(BackupPassphraseEvent.VaultLocked)
                is SetBackupPassphraseUseCase.Result.Failed ->
                    _events.trySend(BackupPassphraseEvent.Error(r.cause.message ?: context.getString(R.string.error_generic)))
            }
            java.util.Arrays.fill(passphrase, '\u0000')
        }
    }

    fun onWipeVault() {
        wipeVault()
        _events.trySend(BackupPassphraseEvent.VaultWiped)
    }
}

sealed interface BackupPassphraseEvent {
    data object Saved : BackupPassphraseEvent
    data object TooShort : BackupPassphraseEvent
    data object VaultLocked : BackupPassphraseEvent
    data object VaultWiped : BackupPassphraseEvent
    data class Error(val message: String) : BackupPassphraseEvent
}
