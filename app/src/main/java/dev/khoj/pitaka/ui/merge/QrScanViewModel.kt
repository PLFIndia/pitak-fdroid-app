package dev.khoj.pitaka.ui.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.prefs.AppPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tiny ViewModel for the library-QR scan flow (PLAN-merge.md D40): persists an
 * adopted library ID. The ID is already validated by [dev.khoj.pitaka.ui.common.QrEncoder]
 * before it reaches here.
 */
@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {
    fun adoptLibraryId(id: String) {
        viewModelScope.launch { prefs.setLibraryId(id) }
    }
}
