package dev.khoj.pitaka.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.crash.CrashReportFile
import dev.khoj.pitaka.data.crash.CrashReportSender
import dev.khoj.pitaka.data.crash.CrashReportStore
import dev.khoj.pitaka.data.images.ImageStore
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.data.prefs.ThemeMode
import dev.khoj.pitaka.data.vault.VaultPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPreferences,
    private val vaultPrefs: VaultPreferences,
    private val imageStore: ImageStore,
    private val bookRepository: dev.khoj.pitaka.domain.repository.BookRepository,
    private val crashReportStore: CrashReportStore,
    private val crashReportSender: CrashReportSender,
) : ViewModel() {

    private val _autoLockMs = MutableStateFlow(vaultPrefs.autoLockTimeoutMs())
    private val _crashReports = MutableStateFlow<List<CrashReportFile>>(emptyList())
    val crashReports: StateFlow<List<CrashReportFile>> = _crashReports

    /**
     * F-04: whether the user has acknowledged that submitting a crash report
     * opens a PUBLIC GitHub issue. Exposed separately (not folded into [state],
     * whose `combine` is already at its 8-arg max) and consumed by the crash
     * report rows to gate the browser open behind the disclosure dialog.
     */
    val crashDisclosureAck: StateFlow<Boolean> = appPrefs.crashDisclosureAck().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setCrashDisclosureAck() {
        viewModelScope.launch { appPrefs.setCrashDisclosureAck(true) }
    }

    // ----- Library ID + maintainer name (PLAN-merge.md D40/D41) -----

    /** This app's library ID (D40). Empty until first generated; getOrCreate on demand. */
    val libraryId: StateFlow<String> = appPrefs.libraryId().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    /** Maintainer attribution handle (D41), blank until set. */
    val maintainerName: StateFlow<String> = appPrefs.maintainerName().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    /** Ensure a library ID exists (idempotent) — called when the Settings row mounts. */
    fun ensureLibraryId() {
        viewModelScope.launch { appPrefs.getOrCreateLibraryId() }
    }

    fun onMaintainerNameChange(name: String) {
        viewModelScope.launch { appPrefs.setMaintainerName(name) }
    }

    /**
     * Generate a BRAND-NEW library ID, detaching this app from any library it was
     * paired with. Destructive to the pairing (not to books): a confirm dialog
     * guards it in the UI. Useful for "start a fresh library on this phone".
     */
    fun onRegenerateLibraryId() {
        viewModelScope.launch {
            appPrefs.setLibraryId(java.util.UUID.randomUUID().toString().replace("-", ""))
        }
    }

    /** Adopt a library ID scanned from another app's QR (D40 pairing). */
    fun onAdoptScannedLibraryId(id: String) {
        viewModelScope.launch { appPrefs.setLibraryId(id) }
    }

    init {
        refreshCrashReports()
    }

    val state: StateFlow<SettingsUiState> = combine(
        appPrefs.themeMode(),
        appPrefs.useDynamicColor(),
        _autoLockMs,
        appPrefs.libraryName(),
        appPrefs.libraryLogoUri(),
        appPrefs.contributorMode(),
        appPrefs.showTranslatableHints(),
        appPrefs.libraryNameLocal(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            useDynamicColor = values[1] as Boolean,
            autoLockMs = values[2] as Long,
            libraryName = values[3] as String,
            libraryLogoUri = values[4] as String,
            contributorMode = values[5] as Boolean,
            showTranslatableHints = values[6] as Boolean,
            libraryNameLocal = values[7] as String,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(autoLockMs = vaultPrefs.autoLockTimeoutMs()),
    )

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { appPrefs.setThemeMode(mode) }
    }

    fun onDynamicColorToggle(enabled: Boolean) {
        viewModelScope.launch { appPrefs.setUseDynamicColor(enabled) }
    }

    fun onAutoLockChange(ms: Long) {
        _autoLockMs.value = ms
        viewModelScope.launch { vaultPrefs.setAutoLockTimeoutMs(ms) }
    }

    fun onLibraryNameChange(name: String) {
        viewModelScope.launch { appPrefs.setLibraryName(name) }
    }

    fun onLibraryNameLocalChange(name: String) {
        viewModelScope.launch { appPrefs.setLibraryNameLocal(name) }
    }

    fun onLibraryLogoPicked(uri: Uri) {
        viewModelScope.launch {
            val fileUri = imageStore.importLibraryLogo(uri) ?: return@launch
            appPrefs.setLibraryLogoUri(fileUri)
        }
    }

    fun onLibraryLogoCleared() {
        viewModelScope.launch {
            imageStore.clearLibraryLogo()
            appPrefs.clearLibraryLogoUri()
        }
    }

    fun onContributorModeToggle(enabled: Boolean) {
        viewModelScope.launch { appPrefs.setContributorMode(enabled) }
    }

    fun onShowTranslatableHintsToggle(enabled: Boolean) {
        viewModelScope.launch { appPrefs.setShowTranslatableHints(enabled) }
    }

    private val _seedStatus = MutableStateFlow<String?>(null)
    /** Debug-only seed status text (null = idle / no message). */
    val seedStatus: StateFlow<String?> = _seedStatus

    /**
     * DEBUG-ONLY: bulk-insert [count] synthetic books and report how long the
     * insert took. Used to measure heavy-library performance on-device. The UI
     * entry point is gated behind BuildConfig.DEBUG.
     */
    fun onSeedTestBooks(count: Int) {
        viewModelScope.launch {
            _seedStatus.value = "Seeding $count books…"
            try {
                val elapsed = kotlin.system.measureTimeMillis {
                    bookRepository.seedRandomBooks(count)
                }
                _seedStatus.value = "Seeded $count books in ${elapsed} ms"
            } catch (e: Exception) {
                _seedStatus.value = "Seed failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** DEBUG-ONLY: surgically remove only the seeded test books. */
    fun onDeleteTestBooks() {
        viewModelScope.launch {
            _seedStatus.value = "Deleting test books…"
            try {
                val removed = bookRepository.deleteSeedBooks()
                _seedStatus.value = "Deleted $removed test books (real books untouched)"
            } catch (e: Exception) {
                _seedStatus.value = "Delete failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ----- Crash reports (Post-1.0) -----

    /** Reload the on-disk list of crash reports into the UI state. Cheap. */
    fun refreshCrashReports() {
        _crashReports.value = crashReportStore.list()
    }

    /**
     * Prepare a GitHub Issue Form URL for [report] and copy the full trace
     * to the clipboard. Returns the URL (caller opens it via the same
     * `openInBrowser` helper the translation flow uses), or `null` if the
     * file could not be read.
     *
     * Note: the report file is NOT deleted automatically. The user gets to
     * decide whether the browser trip succeeded — if it didn't, the banner
     * and Settings entry remain on next launch so they can retry.
     */
    fun prepareCrashSubmission(ctx: Context, report: CrashReportFile): String? =
        crashReportSender.prepare(ctx, report)?.url

    /** Copy the full trace to the clipboard without opening a browser. */
    fun copyCrashReport(ctx: Context, report: CrashReportFile): Boolean =
        crashReportSender.copy(ctx, report)

    /** Delete one crash report and refresh the list. */
    fun deleteCrashReport(report: CrashReportFile) {
        crashReportSender.delete(report)
        refreshCrashReports()
    }

    /** Delete every crash report and refresh the list. */
    fun deleteAllCrashReports() {
        crashReportSender.deleteAll()
        refreshCrashReports()
    }
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val useDynamicColor: Boolean = false,
    val autoLockMs: Long = VaultPreferences.DEFAULT_AUTO_LOCK_MS,
    val libraryName: String = "",
    val libraryNameLocal: String = "",
    val libraryLogoUri: String = "",
    val contributorMode: Boolean = false,
    val showTranslatableHints: Boolean = false,
)
