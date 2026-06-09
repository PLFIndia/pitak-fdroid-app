package dev.khoj.pitaka.ui.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.publish.github.GitHubCredentialStore
import dev.khoj.pitaka.data.publish.github.GitHubDeviceFlow
import dev.khoj.pitaka.domain.model.GitHubClientId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitHubAuthViewModel @Inject constructor(
    private val deviceFlow: GitHubDeviceFlow,
    private val credentials: GitHubCredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(GitHubAuthUiState(
        clientId = credentials.clientId().orEmpty(),
    ))
    val state: StateFlow<GitHubAuthUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun onClientIdChange(v: String) {
        // Clear any stale validation error as the user edits.
        _state.value = _state.value.copy(clientId = v, clientIdError = false)
    }
    fun onPatChange(v: String) {
        _state.value = _state.value.copy(patInput = v)
    }

    fun onSavePat() {
        val pat = _state.value.patInput.trim()
        if (pat.isEmpty()) return
        credentials.setToken(pat)
        _state.value = _state.value.copy(patInput = "", patSaved = true)
    }

    fun onStartOauth() {
        val id = _state.value.clientId.trim()
        // F-15: validate the pasted Client ID locally so an obvious paste
        // mistake produces an actionable inline error instead of an opaque
        // "Failed to start device flow" after a network round-trip.
        val normalized = GitHubClientId.normalizeOrNull(id)
        if (normalized == null) {
            _state.value = _state.value.copy(clientIdError = true)
            return
        }
        credentials.setClientId(normalized)
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            deviceFlow.start(normalized).collect { fs ->
                _state.value = _state.value.copy(flowState = fs)
                if (fs is GitHubDeviceFlow.FlowState.Success) {
                    credentials.setToken(fs.accessToken)
                }
            }
        }
    }
}

data class GitHubAuthUiState(
    val clientId: String = "",
    val clientIdError: Boolean = false,
    val patInput: String = "",
    val patSaved: Boolean = false,
    val flowState: GitHubDeviceFlow.FlowState? = null,
)
