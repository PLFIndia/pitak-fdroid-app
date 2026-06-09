package dev.khoj.pitaka.data.security

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
 * Re-locks App Lock after a configurable timeout once the app is fully
 * backgrounded (ProcessLifecycleOwner.STOPPED). Mirrors VaultAutoLocker so the
 * behaviour is consistent; uses App Lock's own timeout.
 *
 * Decision D-applock-2: launch + re-lock after a background grace timeout.
 */
@Singleton
class AppLockAutoLocker @Inject constructor(
    private val state: AppLockState,
    private val manager: AppLockManager,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lockJob: Job? = null

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Back to foreground — cancel any pending re-lock.
        lockJob?.cancel()
        lockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!manager.isEnabled()) return
        if (state.isLocked()) return
        val timeoutMs = manager.autoLockTimeoutMs()
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(timeoutMs)
            state.lock()
        }
    }
}
