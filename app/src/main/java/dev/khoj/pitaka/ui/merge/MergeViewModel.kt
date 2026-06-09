package dev.khoj.pitaka.ui.merge

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.usecase.MergeLibraryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Drives the community-library merge UI (PLAN-merge.md D40). Reads a picked file,
 * runs [MergeLibraryUseCase], and exposes a UI state that is one of:
 *  - idle (waiting for the user to pick a file),
 *  - running,
 *  - merged (IDs matched / Join applied) — show the add/review summary,
 *  - differ (IDs differ) — show the Join / Overwrite decision,
 *  - error.
 */
@HiltViewModel
class MergeViewModel @Inject constructor(
    private val app: Application,
    private val mergeUseCase: MergeLibraryUseCase,
) : AndroidViewModel(app) {

    sealed interface UiState {
        data object Idle : UiState
        data object Running : UiState
        data class Merged(val result: MergeLibraryUseCase.MergeResult) : UiState
        data class Differ(val decision: MergeLibraryUseCase.Outcome.DiffersDecision) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = UiState.Running
            val text = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }
            }.getOrNull()
            if (text.isNullOrEmpty()) {
                _state.value = UiState.Error("Couldn't read that file.")
                return@launch
            }
            _state.value = when (val outcome = mergeUseCase(text)) {
                is MergeLibraryUseCase.Outcome.Merged -> UiState.Merged(outcome.result)
                is MergeLibraryUseCase.Outcome.DiffersDecision -> UiState.Differ(outcome)
                is MergeLibraryUseCase.Outcome.Failed -> UiState.Error(outcome.message)
            }
        }
    }

    fun onJoin(decision: MergeLibraryUseCase.Outcome.DiffersDecision) {
        viewModelScope.launch {
            _state.value = UiState.Running
            val result = mergeUseCase.applyJoin(decision)
            _state.value = UiState.Merged(result)
        }
    }

    fun onOverwrite(decision: MergeLibraryUseCase.Outcome.DiffersDecision) {
        viewModelScope.launch {
            _state.value = UiState.Running
            mergeUseCase.applyOverwrite(decision)
            // Overwrite replaces wholesale — report as a merge with the incoming count.
            _state.value = UiState.Merged(
                MergeLibraryUseCase.MergeResult(
                    added = decision.incomingBooks.size,
                    identical = 0,
                    conflicts = emptyList(),
                    possibleDuplicates = emptyList(),
                )
            )
        }
    }

    fun reset() { _state.value = UiState.Idle }
}
