package dev.khoj.pitaka.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.prefs.BookmarkStore
import dev.khoj.pitaka.domain.model.Bookmark
import dev.khoj.pitaka.domain.model.BookmarkUrl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val store: BookmarkStore,
) : ViewModel() {

    val state: StateFlow<BookmarksUiState> =
        store.bookmarks()
            .map { BookmarksUiState(bookmarks = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = BookmarksUiState(),
            )

    /**
     * Validates [rawUrl] (decision B: well-formed https, any host) and a
     * non-blank [name], then appends. Returns true on success; false when the
     * input is rejected so the caller can keep the dialog open with an error.
     */
    fun add(name: String, rawUrl: String): Boolean {
        val trimmedName = name.trim()
        val url = BookmarkUrl.normalizeOrNull(rawUrl)
        if (trimmedName.isEmpty() || url == null) return false
        viewModelScope.launch { store.add(Bookmark(name = trimmedName, url = url)) }
        return true
    }

    /** Renames the bookmark at [index]; keeps its URL. No-op for a blank name. */
    fun rename(index: Int, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return
        val current = state.value.bookmarks.getOrNull(index) ?: return
        viewModelScope.launch {
            store.update(index, current.copy(name = trimmedName))
        }
    }

    fun delete(index: Int) {
        viewModelScope.launch { store.removeAt(index) }
    }
}
