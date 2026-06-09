package dev.khoj.pitaka.data.vault

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks the vault after a configurable timeout once the app is fully
 * backgrounded (ProcessLifecycleOwner.STOPPED).
 *
 * D1: default 60 seconds; user can override in Settings (Phase 7).
 *
 * Per the spec we lock on app-level STOPPED rather than per-screen onStop
 * to avoid races during nav transitions between vault-aware screens.
 */
@Singleton
class VaultAutoLocker @Inject constructor(
    private val session: VaultSession,
    private val preferences: VaultPreferences,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lockJob: Job? = null

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun detach() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        scope.cancel()
    }

    override fun onStart(owner: LifecycleOwner) {
        // App came back to the foreground — cancel any pending lock.
        lockJob?.cancel()
        lockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!session.isUnlocked()) return
        val timeoutMs = preferences.autoLockTimeoutMs()
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(timeoutMs)
            session.lock()
        }
    }
}
