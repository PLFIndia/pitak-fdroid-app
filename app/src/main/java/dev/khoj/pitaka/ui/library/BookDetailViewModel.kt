package dev.khoj.pitaka.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.images.ImageStore
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repository: BookRepository,
    private val loans: LoanRepository,
    private val imageStore: ImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    // Tracks whether the first emission has arrived, so we can distinguish
    // "still loading" from "loaded but null (not found)".
    private val loaded = MutableStateFlow(false)

    val state: StateFlow<BookDetailUiState> = combine(
        repository.observeById(bookId),
        // null = vault locked → Available is hidden (D4: count would leak loan state).
        loans.observeActiveCountForBook(bookId),
        loaded,
    ) { book, activeLoans, hasLoaded ->
        BookDetailUiState(
            book = book,
            isLoading = !hasLoaded,
            notFound = hasLoaded && book == null,
            activeLoanCount = activeLoans,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookDetailUiState(),
    )

    private val _events = Channel<BookDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Flip `loaded` once the book flow has produced its first value, so the
        // UI can tell "loading" apart from "not found".
        viewModelScope.launch {
            repository.observeById(bookId).collect { loaded.value = true }
        }
    }

    /**
     * "Remove from library" is now a SOFT delete (PLAN-merge.md): the row and
     * all its metadata stay, flagged `removed`. The book becomes visible-but-inert
     * (badge, actions blocked) and is stripped from publish; the flag merges like
     * any other field across maintainers' devices.
     *
     * No vault gate is needed (unlike the old hard delete): nothing is destroyed,
     * so loan rows that reference this book are NOT orphaned — they stay valid and
     * resolve normally. This is strictly safer than the previous hard delete,
     * which is why the D3 vault-unlock-to-purge-loans requirement falls away here.
     */
    fun onRemoveConfirmed() {
        viewModelScope.launch {
            repository.markRemoved(bookId)
            _events.trySend(BookDetailEvent.Removed)
        }
    }

    /** Inverse of [onRemoveConfirmed]: clear the removed flag (restore to library). */
    fun onRestore() {
        viewModelScope.launch {
            repository.restore(bookId)
            _events.trySend(BookDetailEvent.Restored)
        }
    }

    /**
     * D30: copyCount is freely editable, but the detail screen is display-only
     * (D9 — edits route through the AddBook/edit form, the single mutation path
     * for book fields). Quantity is edited there alongside every other field;
     * this screen only shows Quantity and the derived Available.
     */

    fun onPickCover(uri: android.net.Uri) {
        viewModelScope.launch {
            val existing = repository.getById(bookId) ?: return@launch
            val relativeUrl = imageStore.importBookCover(uri) ?: return@launch
            // Drop the previous local cover file (if any) so we don't orphan
            // bytes. New file is under a fresh UUID, so this never races the
            // file we just wrote.
            imageStore.deleteCoverByUrl(existing.coverUrl)
            repository.upsert(existing.copy(coverUrl = relativeUrl))
        }
    }

    fun onClearCover() {
        viewModelScope.launch {
            val existing = repository.getById(bookId) ?: return@launch
            imageStore.deleteCoverByUrl(existing.coverUrl)
            repository.upsert(existing.copy(coverUrl = null))
        }
    }
}

data class BookDetailUiState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /**
     * Active (not-yet-returned) loans for this book, or null when the vault is
     * locked. When non-null, the UI shows Available = copyCount − activeLoanCount.
     * When null, Available is hidden (D4) and only Quantity is shown.
     */
    val activeLoanCount: Int? = null,
) {
    /** Available copies = quantity − active loans, floored at 0. Null when vault locked. */
    val availableCount: Int?
        get() = book?.let { b -> activeLoanCount?.let { (b.copyCount - it).coerceAtLeast(0) } }
}

sealed interface BookDetailEvent {
    /** The book was soft-removed (flagged `removed`, still in the DB). */
    data object Removed : BookDetailEvent
    /** A previously-removed book was restored to the library. */
    data object Restored : BookDetailEvent
}
