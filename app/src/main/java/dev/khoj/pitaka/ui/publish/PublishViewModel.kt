package dev.khoj.pitaka.ui.publish

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.publish.github.GitHubContentsApi
import dev.khoj.pitaka.data.publish.github.GitHubCredentialStore
import dev.khoj.pitaka.data.publish.github.GitHubRepoDto
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.usecase.PublishLibraryUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublishViewModel @Inject constructor(
    private val credentials: GitHubCredentialStore,
    private val contentsApi: GitHubContentsApi,
    private val publish: PublishLibraryUseCase,
    private val prefs: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PublishUiState())
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    private val _events = Channel<PublishEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
        loadContact()
    }

    /** Loads the persisted contact fields into the UI once at start. */
    private fun loadContact() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                contactLocation = prefs.publishContactLocation().first(),
                contactEmail = prefs.publishContactEmail().first(),
                contactPhone = prefs.publishContactPhone().first(),
            )
        }
    }

    fun onContactLocationChange(value: String) {
        _state.value = _state.value.copy(contactLocation = value)
        viewModelScope.launch { prefs.setPublishContactLocation(value) }
    }

    fun onContactEmailChange(value: String) {
        _state.value = _state.value.copy(contactEmail = value)
        viewModelScope.launch { prefs.setPublishContactEmail(value) }
    }

    fun onContactPhoneChange(value: String) {
        _state.value = _state.value.copy(contactPhone = value)
        viewModelScope.launch { prefs.setPublishContactPhone(value) }
    }

    fun refresh() {
        _state.value = _state.value.copy(
            signedIn = credentials.isAuthenticated(),
            targetRepo = credentials.targetRepo(),
        )
        if (credentials.isAuthenticated() && _state.value.username == null) {
            viewModelScope.launch {
                val name = runCatching {
                    contentsApi.currentUser("Bearer ${credentials.currentToken()}").login
                }.getOrNull()
                _state.value = _state.value.copy(username = name)
            }
        }
    }

    fun onPickRepo() {
        if (!credentials.isAuthenticated()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingRepos = true, repos = emptyList())
            val list = runCatching {
                contentsApi.userRepos("Bearer ${credentials.currentToken()}")
            }.getOrNull().orEmpty()
            _state.value = _state.value.copy(isLoadingRepos = false, repos = list)
        }
    }

    fun onChooseRepo(repo: GitHubRepoDto) {
        val full = repo.fullName ?: return
        credentials.setTargetRepo(full)
        _state.value = _state.value.copy(targetRepo = full, repos = emptyList())
    }

    fun onSignOut() {
        credentials.clearToken()
        credentials.clearTargetRepo()
        // Keep contact fields (independent of GitHub auth); just reset auth state.
        _state.value = _state.value.copy(
            signedIn = false,
            username = null,
            targetRepo = null,
            isLoadingRepos = false,
            repos = emptyList(),
            pagesUrl = null,
            lastResult = null,
        )
    }

    fun onPublishNow() {
        if (!credentials.isAuthenticated() || credentials.targetRepo() == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(publishing = true, lastResult = null, phase = null)
            val r = publish(onPhase = { phase ->
                _state.value = _state.value.copy(phase = phase)
            })
            when (r) {
                is PublishLibraryUseCase.Result.Success -> {
                    val parts = mutableListOf(context.getString(R.string.publish_result_success, r.files.size))
                    // Pages build status (best-effort). null = unknown/no scope.
                    when (r.pagesLive) {
                        true -> parts += context.getString(R.string.publish_pages_live)
                        false -> parts += context.getString(R.string.publish_pages_build_failed)
                        null -> parts += context.getString(R.string.publish_pages_building)
                    }
                    if (r.availabilityOmitted) {
                        parts += context.getString(R.string.publish_availability_omitted_hint)
                    }
                    _state.value = _state.value.copy(
                        publishing = false,
                        phase = null,
                        lastResult = parts.joinToString("\n"),
                        pagesUrl = r.pagesUrl,
                    )
                }
                is PublishLibraryUseCase.Result.Failed ->
                    _state.value = _state.value.copy(
                        publishing = false,
                        phase = null,
                        lastResult = r.reason,
                        pagesUrl = null,
                    )
            }
        }
    }
}

data class PublishUiState(
    val signedIn: Boolean = false,
    val username: String? = null,
    val targetRepo: String? = null,
    val isLoadingRepos: Boolean = false,
    val repos: List<GitHubRepoDto> = emptyList(),
    val publishing: Boolean = false,
    /** Current coarse publish phase while [publishing]; null when idle. */
    val phase: PublishLibraryUseCase.Phase? = null,
    val pagesUrl: String? = null,
    val lastResult: String? = null,
    val contactLocation: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
)

sealed interface PublishEvent
