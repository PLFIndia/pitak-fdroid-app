package dev.khoj.pitaka.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.repository.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    appPrefs: AppPreferences,
    bookRepository: BookRepository,
) : ViewModel() {

    val state: StateFlow<WelcomeUiState> = combine(
        appPrefs.libraryLogoUri(),
        appPrefs.libraryName(),
        appPrefs.libraryNameLocal(),
    ) { logoUri, name, nameLocal ->
        WelcomeUiState(
            libraryLogoUri = logoUri,
            libraryName = name,
            libraryNameLocal = nameLocal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(2_000),
        initialValue = WelcomeUiState(),
    )

    /**
     * Flips to `true` once the library data has loaded (Room emits the first
     * book list when the query completes). The welcome screen waits for this —
     * past a minimum hold — before dismissing, so the Library is already
     * populated when the overlay fades away. Starts eagerly so the load runs
     * during the welcome hold rather than after it.
     */
    val libraryReady: StateFlow<Boolean> = bookRepository.observeAll()
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
}

data class WelcomeUiState(
    val libraryLogoUri: String = "",
    val libraryName: String = "",
    val libraryNameLocal: String = "",
)
