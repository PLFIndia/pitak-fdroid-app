package dev.khoj.pitaka.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.security.AppLockManager
import dev.khoj.pitaka.data.security.AppLockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives both the App Lock settings section (enable/disable/change/biometric)
 * and the lock-screen gate (verify PIN). Argon2 hashing runs off the main
 * thread.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val manager: AppLockManager,
    private val appLockState: AppLockState,
) : ViewModel() {

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<AppLockUiState> = _ui.asStateFlow()

    val locked: StateFlow<Boolean> = appLockState.locked

    private fun snapshot() = AppLockUiState(
        enabled = manager.isEnabled(),
        biometricEnabled = manager.isBiometricEnabled(),
        autoLockTimeoutMs = manager.autoLockTimeoutMs(),
    )

    private fun refresh() { _ui.value = snapshot() }

    /** Enable App Lock by setting an initial PIN. Locks the gate on success. */
    fun enableWithPin(pin: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { manager.setPin(pin) }
            pin.fill('\u0000')
            if (ok) {
                appLockState.onEnabledChanged()
                refresh()
            }
            onResult(ok)
        }
    }

    fun changePin(currentPin: CharArray, newPin: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val verify = withContext(Dispatchers.Default) { manager.verifyPin(currentPin) }
            currentPin.fill('\u0000')
            if (verify is AppLockManager.Result.Success) {
                val ok = withContext(Dispatchers.Default) { manager.setPin(newPin) }
                newPin.fill('\u0000')
                refresh()
                onResult(ok)
            } else {
                newPin.fill('\u0000')
                onResult(false)
            }
        }
    }

    /** Disable requires the current PIN. */
    fun disableWithPin(currentPin: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val verify = withContext(Dispatchers.Default) { manager.verifyPin(currentPin) }
            currentPin.fill('\u0000')
            if (verify is AppLockManager.Result.Success) {
                manager.disable()
                appLockState.unlock()
                refresh()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    /** Forgot-PIN reset path — caller has already confirmed device credential. */
    fun resetAfterDeviceAuth() {
        manager.disable()
        appLockState.unlock()
        refresh()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        manager.setBiometricEnabled(enabled)
        refresh()
    }

    fun setAutoLockTimeout(ms: Long) {
        manager.setAutoLockTimeoutMs(ms)
        refresh()
    }

    /** Verify a PIN at the lock screen. Unlocks the gate on success. */
    fun verifyAtGate(pin: CharArray, onResult: (AppLockManager.Result) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { manager.verifyPin(pin) }
            pin.fill('\u0000')
            if (result is AppLockManager.Result.Success) {
                appLockState.unlock()
            }
            onResult(result)
        }
    }

    /** Biometric success at the gate. */
    fun unlockViaBiometric() {
        appLockState.unlock()
    }
}

data class AppLockUiState(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutMs: Long = AppLockManager.DEFAULT_AUTOLOCK_MS,
)
