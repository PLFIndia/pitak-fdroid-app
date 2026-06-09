package dev.khoj.pitaka.ui.crash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.crash.CrashReportFile
import dev.khoj.pitaka.data.crash.CrashReportSender
import dev.khoj.pitaka.data.crash.CrashReportStore
import dev.khoj.pitaka.data.prefs.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lightweight ViewModel owned by the Library top-of-screen crash banner.
 *
 * Kept separate from `LibraryViewModel` so the Library screen stays
 * focused on books (single responsibility) and the banner can drop its
 * own state when no crashes are pending — the banner composable returns
 * zero pixels in that common case.
 *
 * State is loaded on init (cheap directory listing) and refreshed when
 * the user takes action via this VM (send / copy / delete / dismiss).
 */
@HiltViewModel
class CrashBannerViewModel @Inject constructor(
    private val store: CrashReportStore,
    private val sender: CrashReportSender,
    private val appPrefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(State(pendingReports = store.list()))
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * F-04: whether the user has acknowledged that sending a crash report
     * opens a PUBLIC GitHub issue. Gates the banner's Send button behind the
     * disclosure dialog on first use. Shared flag with the Settings crash flow
     * (both are the same crash payload).
     */
    val disclosureAck: StateFlow<Boolean> = appPrefs.crashDisclosureAck().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setDisclosureAck() {
        viewModelScope.launch { appPrefs.setCrashDisclosureAck(true) }
    }

    /** Re-read disk; called from a [LaunchedEffect] on the Library screen. */
    fun refresh() {
        _state.value = _state.value.copy(pendingReports = store.list())
    }

    /**
     * Builds the GitHub Issue URL for the newest crash and copies the full
     * trace to the clipboard. Returns the URL for the caller to open via
     * `openInBrowser`. Returns `null` if no reports exist or the file
     * cannot be read.
     */
    fun prepareNewestSubmission(ctx: Context): String? {
        val newest = _state.value.pendingReports.firstOrNull() ?: return null
        return sender.prepare(ctx, newest)?.url
    }

    /** Dismiss the banner without sending — deletes the newest report. */
    fun dismissNewest() {
        viewModelScope.launch {
            val newest = _state.value.pendingReports.firstOrNull() ?: return@launch
            sender.delete(newest)
            refresh()
        }
    }

    /**
     * F-04 "Copy instead": copy the newest trace to the clipboard without
     * opening a browser, so the user can paste it from their own / a throwaway
     * GitHub account. Returns false if there's nothing to copy.
     */
    fun copyNewest(ctx: Context): Boolean {
        val newest = _state.value.pendingReports.firstOrNull() ?: return false
        return sender.copy(ctx, newest)
    }

    data class State(val pendingReports: List<CrashReportFile> = emptyList()) {
        val hasReports: Boolean get() = pendingReports.isNotEmpty()
    }
}
