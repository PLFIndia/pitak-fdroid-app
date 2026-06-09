package dev.khoj.pitaka

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.khoj.pitaka.data.crash.CrashHandler
import dev.khoj.pitaka.data.crash.CrashReportStore
import dev.khoj.pitaka.data.images.CoverHealer
import dev.khoj.pitaka.data.security.AppLockAutoLocker
import dev.khoj.pitaka.data.vault.VaultAutoLocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt entry point. Triggers Dagger code generation at compile time and provides
 * the application-scoped component used by every @Inject / @AndroidEntryPoint
 * site in the app.
 *
 * Phase 4: hooks the [VaultAutoLocker] into ProcessLifecycleOwner so the vault
 * auto-locks per D1 once the app is fully backgrounded for the configured
 * timeout.
 *
 * Post-1.0: also installs [CrashHandler] so uncaught Java/Kotlin exceptions
 * are persisted to a `.txt` file under `filesDir/crashes/` before the
 * process dies, and the user can opt in on next launch to submit the report
 * to the public crash-log GitHub repo (no developer infrastructure).
 */
@HiltAndroidApp
class PitakaApplication : Application() {

    @Inject
    lateinit var autoLocker: VaultAutoLocker

    @Inject
    lateinit var appLockAutoLocker: AppLockAutoLocker

    @Inject
    lateinit var crashReportStore: CrashReportStore

    @Inject
    lateinit var coverHealer: CoverHealer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Install the crash handler FIRST so any crash inside subsequent
        // initialisation still gets captured.
        CrashHandler.install(this, crashReportStore)
        runCatching { System.loadLibrary("sqlcipher") }
        autoLocker.attach()
        appLockAutoLocker.attach()
        // One-shot cover-heal migration (PLAN-covers.md D4). Off the main
        // thread; guarded internally by a DataStore flag so it runs at most
        // once. Touches only the unencrypted books DB + filesDir/covers.
        appScope.launch { runCatching { coverHealer.runIfNeeded() } }
    }
}
