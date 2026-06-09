package dev.khoj.pitaka.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.prefs.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tiny app-root ViewModel — exposes flags that need to be read once at the
 * very top of the composition (currently: contributor mode). Kept separate
 * from feature ViewModels so that root composition stays cheap.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val appPrefs: AppPreferences,
) : ViewModel() {

    val contributorMode: StateFlow<Boolean> = appPrefs.contributorMode().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val showTranslatableHints: StateFlow<Boolean> = appPrefs.showTranslatableHints().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /**
     * F-04: whether the user has acknowledged the pre-submit disclosure for
     * the translation flow. The suggestion sheet lives at the app root with no
     * ViewModel of its own, so this flag is sourced here and threaded down.
     */
    val translateDisclosureAck: StateFlow<Boolean> = appPrefs.translateDisclosureAck().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setTranslateDisclosureAck() {
        viewModelScope.launch { appPrefs.setTranslateDisclosureAck(true) }
    }
}
