package dev.khoj.pitaka.data.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory locked/unlocked state for App Lock, analogous to VaultSession.
 *
 * The app starts locked whenever App Lock is enabled; a successful PIN/biometric
 * unlock flips it to unlocked for the session, and the auto-locker (or process
 * death) flips it back. This holds NO secrets — just a boolean gate.
 */
@Singleton
class AppLockState @Inject constructor(
    private val manager: AppLockManager,
) {
    // Start locked iff App Lock is enabled. Computed lazily on first collection
    // rather than in the constructor, so simply having this in the DI graph
    // (e.g. when PitakaApplication wires the auto-locker) does NOT touch the
    // EncryptedSharedPreferences-backed store — that read hits the Android
    // Keystore, which isn't available under Robolectric unit tests.
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean> = _locked
        .map { it ?: manager.isEnabled() }
        .stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // WhileSubscribed (not Eagerly): the store/Keystore is only read once
            // the UI actually collects `locked` (the app-lock gate), never merely
            // because AppLockState was constructed in the DI graph. Keeps
            // Robolectric unit tests (no Android Keystore) from crashing.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun isLocked(): Boolean = _locked.value ?: manager.isEnabled()

    fun unlock() {
        _locked.value = false
    }

    /** Re-lock — only has an effect when App Lock is enabled. */
    fun lock() {
        if (manager.isEnabled()) _locked.value = true
    }

    /**
     * Re-evaluate after enable/disable changes: enabling locks immediately (so
     * the user confirms the new PIN works on next foreground); disabling unlocks.
     */
    fun onEnabledChanged() {
        _locked.value = manager.isEnabled() && (_locked.value ?: true)
    }
}
