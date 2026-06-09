package dev.khoj.pitaka.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.crypto.KeystoreVault
import dev.khoj.pitaka.data.vault.VaultSession
import dev.khoj.pitaka.data.vault.VaultState
import dev.khoj.pitaka.domain.usecase.EnableVaultUseCase
import dev.khoj.pitaka.domain.usecase.LockVaultUseCase
import dev.khoj.pitaka.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * Drives the vault unlock / enable / lock UI. Single-instance via Hilt;
 * Loans / Borrowers / Pending screens all consume the same state.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val session: VaultSession,
    private val keystore: KeystoreVault,
    private val enableVault: EnableVaultUseCase,
    private val unlockVault: UnlockVaultUseCase,
    private val lockVault: LockVaultUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<VaultUiState> = session.state
        .map { vaultState ->
            VaultUiState(
                isInitialized = keystore.isInitialized(),
                isUnlocked = vaultState is VaultState.Unlocked,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaultUiState(
                isInitialized = keystore.isInitialized(),
                isUnlocked = session.isUnlocked(),
            ),
        )

    private val _events = Channel<VaultEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEnableConfirmed() {
        when (val r = enableVault()) {
            EnableVaultUseCase.Result.Success,
            EnableVaultUseCase.Result.AlreadyEnabled -> _events.trySend(VaultEvent.Unlocked)
            is EnableVaultUseCase.Result.Failed ->
                _events.trySend(VaultEvent.Error(r.cause.message ?: context.getString(R.string.error_generic)))
        }
    }

    fun onUnlockConfirmed() {
        // Called only from the BiometricPrompt success callback (VaultGate) —
        // the authenticated unlock path. Mint the F-06 ticket here so unwrap()
        // can prove it was reached legitimately.
        when (val r = unlockVault(keystore.authorizeUnlock())) {
            UnlockVaultUseCase.Result.Success -> _events.trySend(VaultEvent.Unlocked)
            UnlockVaultUseCase.Result.NotInitialized ->
                _events.trySend(VaultEvent.Error(context.getString(R.string.vault_error_not_initialized)))
            is UnlockVaultUseCase.Result.Failed ->
                _events.trySend(VaultEvent.Error(r.cause.message ?: context.getString(R.string.error_generic)))
        }
    }

    fun onLock() {
        lockVault()
    }
}

data class VaultUiState(
    val isInitialized: Boolean,
    val isUnlocked: Boolean,
)

sealed interface VaultEvent {
    data object Unlocked : VaultEvent
    data class Error(val message: String) : VaultEvent
}
