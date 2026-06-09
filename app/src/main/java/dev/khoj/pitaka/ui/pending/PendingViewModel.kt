package dev.khoj.pitaka.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.usecase.GetPendingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingViewModel @Inject constructor(
    private val getPending: GetPendingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PendingUiState())
    val state: StateFlow<PendingUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            when (val r = getPending()) {
                is GetPendingUseCase.Result.Success ->
                    _state.value = PendingUiState(snapshot = r.snapshot, vaultLocked = false, isLoading = false)
                GetPendingUseCase.Result.VaultLocked ->
                    _state.value = PendingUiState(snapshot = null, vaultLocked = true, isLoading = false)
            }
        }
    }
}

data class PendingUiState(
    val snapshot: GetPendingUseCase.Snapshot? = null,
    val vaultLocked: Boolean = false,
    val isLoading: Boolean = true,
)
