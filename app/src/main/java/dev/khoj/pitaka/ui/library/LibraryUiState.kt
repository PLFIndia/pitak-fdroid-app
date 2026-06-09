package dev.khoj.pitaka.ui.library

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.domain.repository.LibraryFilter

/**
 * State for the Library list screen.
 *
 * D17: the dismissible onboarding card is visible on first-run-with-zero-books and
 *      stays dismissible.
 * D22: default sort is recently-added newest first. Phase 7 persists the user's
 *      choice across sessions via AppPreferences (DataStore-backed).
 * D26: empty-state has at least one action that gets the user somewhere useful.
 */
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val showOnboardingCard: Boolean = true,
    val sort: BookSort = BookSort.RecentlyAdded,
    val libraryName: String = "",
    val libraryLogoUri: String = "",
    /** Active genre/language facets (None = unfiltered). */
    val filter: LibraryFilter = LibraryFilter.None,
    /** Distinct languages present in the library, for the filter chips. */
    val availableLanguages: List<String> = emptyList(),
    /**
     * Book IDs that are currently NOT available (every copy is out on loan),
     * or null when the vault is locked. Null → the list shows no availability
     * info (D4 / D31: lent-state is vault-derived and must not leak while
     * locked). When non-null, a "Not available" badge shows for these IDs.
     */
    val unavailableBookIds: Set<Long>? = null,
)
