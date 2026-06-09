package dev.khoj.pitaka.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.domain.repository.LibraryFilter
import dev.khoj.pitaka.domain.usecase.DeleteBookUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val deleteBook: DeleteBookUseCase,
    private val prefs: AppPreferences,
    private val loans: dev.khoj.pitaka.domain.repository.LoanRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showOnboardingCard = MutableStateFlow(true)
    private val filter = MutableStateFlow(LibraryFilter.None)

    private val booksFlow = combine(query, prefs.librarySort(), filter) { q, sort, f -> Triple(q, sort, f) }
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { (q, sort, f) ->
            if (q.isBlank()) repository.observeAll(sort, f) else repository.search(q, sort, f)
        }

    val state: StateFlow<LibraryUiState> = combine(
        booksFlow,
        query,
        showOnboardingCard,
        prefs.librarySort(),
        prefs.libraryName(),
        prefs.libraryLogoUri(),
        filter,
        repository.observeLanguages(),
        loans.observeActiveLoanCountsByBook(),
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val books = values[0] as List<dev.khoj.pitaka.domain.model.Book>
        val q = values[1] as String
        val showCard = values[2] as Boolean
        val sort = values[3] as dev.khoj.pitaka.domain.repository.BookSort
        val libraryName = values[4] as String
        val libraryLogoUri = values[5] as String
        val activeFilter = values[6] as LibraryFilter
        @Suppress("UNCHECKED_CAST")
        val languages = values[7] as List<String>
        @Suppress("UNCHECKED_CAST")
        val activeLoanCounts = values[8] as Map<Long, Int>?
        // D4 / D31: null when the vault is locked → no availability info. When
        // unlocked, a book is "not available" only when every copy is out
        // (active loans >= copyCount).
        val unavailable: Set<Long>? = activeLoanCounts?.let { counts ->
            books.filter { b -> (counts[b.id] ?: 0) >= b.copyCount }.map { it.id }.toSet()
        }
        LibraryUiState(
            books = books,
            query = q,
            isLoading = false,
            // D17: card is shown only when (a) the user hasn't dismissed it AND
            // (b) the library is empty. As soon as a book exists the card hides
            // automatically; once dismissed it stays hidden for the session.
            showOnboardingCard = showCard && books.isEmpty() && q.isBlank() && !activeFilter.isActive,
            sort = sort,
            libraryName = libraryName,
            libraryLogoUri = libraryLogoUri,
            filter = activeFilter,
            availableLanguages = languages,
            unavailableBookIds = unavailable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onDismissOnboardingCard() {
        showOnboardingCard.value = false
    }

    fun onSortChange(sort: BookSort) {
        viewModelScope.launch { prefs.setLibrarySort(sort) }
    }

    /** Set or clear the language facet (null clears it). */
    fun onLanguageFilterChange(language: String?) {
        filter.value = filter.value.copy(language = language)
    }

    /** Clear all active facets. */
    fun onClearFilters() {
        filter.value = LibraryFilter.None
    }

    fun onDeleteBook(id: Long) {
        viewModelScope.launch { deleteBook(id) }
    }
}
