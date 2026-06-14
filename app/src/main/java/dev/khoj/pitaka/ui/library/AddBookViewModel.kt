package dev.khoj.pitaka.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.TitleSearchResult
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.usecase.AddBookUseCase
import dev.khoj.pitaka.domain.usecase.LookupIsbnUseCase
import dev.khoj.pitaka.domain.usecase.SearchByTitleUseCase
import dev.khoj.pitaka.domain.usecase.UpdateBookUseCase
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Add Book manual-entry + ISBN-lookup + D7 title-search fallback.
 *
 * Phase 2 surface area:
 *  - manual entry as in Phase 1.
 *  - "Lookup ISBN" button → ChainedIsbnLookup (Open Library → Google Books → cache).
 *  - On Found → pre-fill the form, leave editing free.
 *  - On NotFound → emit LookupFailedNotFound event. The screen offers
 *    "Search by title" (D7), "Save with just ISBN" (skeleton, D11), or
 *    "Fill manually" (just close the dialog).
 *  - On NetworkError → emit LookupFailedNetwork event with the same options
 *    plus Retry.
 *  - On scanner hand-off: navigation passes `?isbn=…` → VM pre-fills the
 *    ISBN field only. No automatic network lookup — the user taps "Lookup"
 *    when they want metadata, so all network use stays user-initiated.
 *  - D2 duplicate-ISBN dialog wires Save → findByIsbn → existing.
 */
@HiltViewModel
class AddBookViewModel @Inject constructor(
    private val addBook: AddBookUseCase,
    private val updateBook: UpdateBookUseCase,
    private val repository: BookRepository,
    private val lookupIsbn: LookupIsbnUseCase,
    private val searchByTitle: SearchByTitleUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _form = MutableStateFlow(AddBookFormState())
    val form: StateFlow<AddBookFormState> = _form.asStateFlow()

    private val _events = Channel<AddBookEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Non-zero when editing an existing book; 0 means "adding new". */
    private val editingBookId: Long = savedStateHandle[Routes.ARG_BOOK_ID] ?: 0L

    /** Snapshot of the book at load time — used to detect ISBN change for the D30 warning. */
    private var originalBook: Book? = null

    init {
        val prefilledIsbn: String? = savedStateHandle[Routes.ARG_ISBN]
        when {
            editingBookId != 0L -> loadForEdit(editingBookId)
            !prefilledIsbn.isNullOrBlank() -> {
                // Scanner hand-off pre-fills the ISBN but does NOT auto-trigger a
                // network lookup. The openlibrary.org call only happens when the
                // user explicitly taps "Lookup", keeping all network use
                // user-initiated.
                _form.update { it.copy(isbn = prefilledIsbn) }
            }
        }
    }

    private fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val book = repository.getById(id) ?: run {
                _events.trySend(AddBookEvent.NotFound)
                return@launch
            }
            originalBook = book
            _form.update {
                it.copy(
                    title = book.title,
                    titleTransliteration = book.titleTransliteration.orEmpty(),
                    author = book.author.orEmpty(),
                    isbn = book.isbn.orEmpty(),
                    publisher = book.publisher.orEmpty(),
                    publishedYear = book.publishedYear?.toString().orEmpty(),
                    genre = book.genre.orEmpty(),
                    language = book.language.orEmpty(),
                    pageCount = book.pageCount?.toString().orEmpty(),
                    notes = book.notes.orEmpty(),
                    location = book.location.orEmpty(),
                    quantity = book.copyCount.toString(),
                    sourceType = book.sourceType,
                    sourceDetail = book.sourceDetail.orEmpty(),
                    ageGroup = book.ageGroup,
                    addedDate = book.addedDate,
                    coverUrl = book.coverUrl,
                    mode = AddBookMode.Edit,
                )
            }
        }
    }

    fun onTitleChange(v: String)             = _form.update { it.copy(title = v, titleError = false) }
    fun onTransliterationChange(v: String)   = _form.update { it.copy(titleTransliteration = v) }
    fun onAuthorChange(v: String)            = _form.update { it.copy(author = v) }
    fun onIsbnChange(v: String)              = _form.update { it.copy(isbn = v) }
    fun onPublisherChange(v: String)         = _form.update { it.copy(publisher = v) }
    fun onYearChange(v: String)              = _form.update { it.copy(publishedYear = v) }
    fun onGenreChange(v: String)             = _form.update { it.copy(genre = v) }
    fun onLanguageChange(v: String)          = _form.update { it.copy(language = v) }
    fun onPagesChange(v: String)             = _form.update { it.copy(pageCount = v) }
    fun onNotesChange(v: String)             = _form.update { it.copy(notes = v) }
    fun onLocationChange(v: String)          = _form.update { it.copy(location = v) }
    fun onQuantityChange(v: String)          = _form.update { it.copy(quantity = v.filter(Char::isDigit)) }
    fun onSourceTypeChange(v: Book.SourceType?) = _form.update { it.copy(sourceType = v) }
    fun onSourceDetailChange(v: String)      = _form.update { it.copy(sourceDetail = v) }
    fun onAgeGroupChange(v: Book.AgeGroup?)  = _form.update { it.copy(ageGroup = v) }
    fun onAddedDateChange(epochMillis: Long) = _form.update { it.copy(addedDate = epochMillis) }

    fun onLookupIsbn() {
        val raw = _form.value.isbn
        val normalized = LookupIsbnUseCase.normalize(raw)
        if (normalized.isEmpty()) return
        _form.update { it.copy(isLookingUp = true) }
        viewModelScope.launch {
            val result = lookupIsbn(normalized)
            _form.update { it.copy(isLookingUp = false, isbn = normalized) }
            when (result) {
                is LookupResult.Found -> applyMetadata(result.metadata)
                is LookupResult.NotFound -> _events.trySend(AddBookEvent.LookupNotFound)
                is LookupResult.NetworkError -> _events.trySend(AddBookEvent.LookupNetworkError)
            }
        }
    }

    fun onTitleSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _events.trySend(AddBookEvent.TitleSearchProgress)
            when (val r = searchByTitle(query)) {
                is SearchResult.Found  -> _events.trySend(AddBookEvent.TitleSearchResults(r.results))
                is SearchResult.Empty  -> _events.trySend(AddBookEvent.TitleSearchEmpty)
                is SearchResult.NetworkError -> _events.trySend(AddBookEvent.TitleSearchError)
            }
        }
    }

    fun onPickTitleSearchResult(result: TitleSearchResult) {
        _form.update { current ->
            current.copy(
                title = result.title,
                author = result.author.orEmpty(),
                publishedYear = result.publishedYear?.toString().orEmpty(),
                isbn = result.isbn.orEmpty(),
            )
        }
    }

    fun onSaveSkeletonWithIsbn() {
        // D11: save what we have plus the needsMetadata flag.
        val current = _form.value
        viewModelScope.launch {
            val candidate = current
                .copy(title = current.title.ifBlank { "ISBN ${current.isbn.trim()}" })
                .toBook()
                .copy(needsMetadata = true)
            when (val r = addBook(candidate)) {
                is AddBookUseCase.Result.Success -> _events.trySend(AddBookEvent.Saved(id = r.id))
                AddBookUseCase.Result.TitleRequired -> _form.update { it.copy(titleError = true) }
            }
        }
    }

    fun onSave() {
        val current = _form.value
        if (current.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        // EDIT mode
        if (current.mode == AddBookMode.Edit) {
            val original = originalBook ?: return
            val newIsbn = LookupIsbnUseCase.normalize(current.isbn)
            val oldIsbn = original.isbn?.let(LookupIsbnUseCase::normalize).orEmpty()
            if (newIsbn != oldIsbn && newIsbn.isNotEmpty()) {
                // D30: warn user that ISBN edit invalidates the cached metadata
                // for the old ISBN. Caller can confirm via [onConfirmSaveAfterIsbnChange].
                _events.trySend(AddBookEvent.IsbnChangedConfirmRequested)
                return
            }
            performUpdate(current)
            return
        }
        // ADD mode
        viewModelScope.launch {
            val normalized = LookupIsbnUseCase.normalize(current.isbn)
            if (normalized.isNotEmpty()) {
                val existing = repository.findByIsbn(normalized)
                if (existing != null) {
                    _events.trySend(AddBookEvent.DuplicateIsbn(existingId = existing.id))
                    return@launch
                }
            }
            when (val r = addBook(current.toBook())) {
                is AddBookUseCase.Result.Success -> _events.trySend(AddBookEvent.Saved(id = r.id))
                AddBookUseCase.Result.TitleRequired -> _form.update { it.copy(titleError = true) }
            }
        }
    }

    /** Called when the user confirms the D30 ISBN-change dialog. */
    fun onConfirmSaveAfterIsbnChange() {
        performUpdate(_form.value)
    }

    private fun performUpdate(current: AddBookFormState) {
        val original = originalBook ?: return
        viewModelScope.launch {
            // Preserve immutable id from the original; everything else (incl.
            // the now-editable addedDate "Date added") comes from the form.
            val updated = current.toBook().copy(
                id = original.id,
                needsMetadata = original.needsMetadata && !hasCoreMetadata(current),
            )
            when (val r = updateBook(updated)) {
                UpdateBookUseCase.Result.Success -> _events.trySend(AddBookEvent.Saved(id = original.id))
                UpdateBookUseCase.Result.TitleRequired -> _form.update { it.copy(titleError = true) }
                UpdateBookUseCase.Result.NotFound -> _events.trySend(AddBookEvent.NotFound)
                UpdateBookUseCase.Result.IdImmutable -> {
                    // We control the call site and never change the id, so this is a
                    // programmer error if it ever fires. Surface as a generic failure.
                    _events.trySend(AddBookEvent.NotFound)
                }
            }
        }
    }

    fun onAddAsDuplicateCopy(existingId: Long) {
        viewModelScope.launch {
            val existing = repository.getById(existingId) ?: return@launch
            val updated = existing.copy(copyCount = existing.copyCount + 1)
            repository.upsert(updated)
            _events.trySend(AddBookEvent.Saved(id = existingId))
        }
    }

    /**
     * True when the book now carries the essential metadata, so the D11
     * "needs metadata" badge should no longer apply. A skeleton scan saves with
     * a placeholder title of the form "ISBN <isbn>" and no author; once the user
     * supplies a real title (not that placeholder) AND an author, we consider the
     * core metadata present. Title alone isn't enough — a bare title with no
     * author is still a stub worth flagging.
     */
    private fun hasCoreMetadata(form: AddBookFormState): Boolean {
        val title = form.title.trim()
        val author = form.author.trim()
        val isbn = form.isbn.trim()
        val placeholderTitle = isbn.isNotEmpty() && title.equals("ISBN $isbn", ignoreCase = true)
        return title.isNotEmpty() && !placeholderTitle && author.isNotEmpty()
    }

    private fun applyMetadata(metadata: BookMetadata) {
        _form.update { current ->
            current.copy(
                title = current.title.ifBlank { metadata.title.orEmpty() },
                author = current.author.ifBlank { metadata.author.orEmpty() },
                publisher = current.publisher.ifBlank { metadata.publisher.orEmpty() },
                publishedYear = current.publishedYear.ifBlank {
                    metadata.publishedYear?.toString().orEmpty()
                },
                genre = current.genre.ifBlank { metadata.genre.orEmpty() },
                language = current.language.ifBlank { metadata.language.orEmpty() },
                pageCount = current.pageCount.ifBlank {
                    metadata.pageCount?.toString().orEmpty()
                },
                coverUrl = metadata.coverUrl,
                titleError = false,
            )
        }
    }
}

data class AddBookFormState(
    val mode: AddBookMode = AddBookMode.Add,
    val title: String = "",
    val titleTransliteration: String = "",
    val author: String = "",
    val isbn: String = "",
    val publisher: String = "",
    val publishedYear: String = "",
    val genre: String = "",
    val language: String = "",
    val pageCount: String = "",
    val notes: String = "",
    val location: String = "",
    /** Quantity (copyCount) as an editable string; blank/0 coerces to 1 in toBook. */
    val quantity: String = "1",
    /** Provenance category (nullable — blank means "not set"). */
    val sourceType: Book.SourceType? = null,
    /** Free-form bilingual provenance specifics. */
    val sourceDetail: String = "",
    /** Reader age band (nullable — blank means "not set"). */
    val ageGroup: Book.AgeGroup? = null,
    /**
     * "Date added" (acquisition date). Epoch millis; defaults to today on a
     * fresh form, user-editable via the date picker. Drives the Recently-added
     * sort, so back-dating moves the book down that order (intended).
     */
    val addedDate: Long = System.currentTimeMillis(),
    val coverUrl: String? = null,
    val titleError: Boolean = false,
    val isLookingUp: Boolean = false,
) {
    fun toBook(): Book = Book(
        title = title.trim(),
        titleTransliteration = titleTransliteration.trim().ifBlank { null },
        author = author.trim().ifBlank { null },
        isbn = isbn.trim().ifBlank { null },
        publisher = publisher.trim().ifBlank { null },
        publishedYear = publishedYear.trim().toIntOrNull(),
        genre = genre.trim().ifBlank { null },
        language = language.trim().ifBlank { null },
        pageCount = pageCount.trim().toIntOrNull(),
        notes = notes.trim().ifBlank { null },
        location = location.trim().ifBlank { null },
        // Quantity floors at 1: a book in the library is at least one copy.
        copyCount = quantity.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
        sourceType = sourceType,
        sourceDetail = sourceDetail.trim().ifBlank { null },
        ageGroup = ageGroup,
        addedDate = addedDate,
        coverUrl = coverUrl,
    )
}

sealed interface AddBookEvent {
    data class Saved(val id: Long) : AddBookEvent
    data class DuplicateIsbn(val existingId: Long) : AddBookEvent
    data object LookupNotFound : AddBookEvent
    data object LookupNetworkError : AddBookEvent
    data object TitleSearchProgress : AddBookEvent
    data class TitleSearchResults(val results: List<TitleSearchResult>) : AddBookEvent
    data object TitleSearchEmpty : AddBookEvent
    data object TitleSearchError : AddBookEvent
    /** Edit-mode: user changed the ISBN and we need a confirm dialog (D30). */
    data object IsbnChangedConfirmRequested : AddBookEvent
    /** Edit-mode: the book being edited no longer exists. */
    data object NotFound : AddBookEvent
}

enum class AddBookMode { Add, Edit }
