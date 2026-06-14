package dev.khoj.pitaka.ui.wishlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import dev.khoj.pitaka.domain.usecase.AddWishlistBookUseCase
import dev.khoj.pitaka.domain.usecase.LookupIsbnUseCase
import dev.khoj.pitaka.domain.usecase.UpdateWishlistBookUseCase
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddWishlistViewModel @Inject constructor(
    private val addBook: AddWishlistBookUseCase,
    private val updateBook: UpdateWishlistBookUseCase,
    private val repository: WishlistRepository,
    private val lookupIsbn: LookupIsbnUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _form = MutableStateFlow(AddWishlistFormState())
    val form: StateFlow<AddWishlistFormState> = _form.asStateFlow()

    private val _events = Channel<AddWishlistEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val editingId: Long = savedStateHandle[Routes.ARG_BOOK_ID] ?: 0L
    private var originalBook: WishlistBook? = null

    init {
        val prefilledIsbn: String? = savedStateHandle[Routes.ARG_ISBN]
        when {
            editingId != 0L -> loadForEdit(editingId)
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
            val w = repository.getById(id) ?: run {
                _events.trySend(AddWishlistEvent.NotFound); return@launch
            }
            originalBook = w
            _form.update {
                it.copy(
                    mode = AddWishlistMode.Edit,
                    title = w.title,
                    titleTransliteration = w.titleTransliteration.orEmpty(),
                    author = w.author.orEmpty(),
                    isbn = w.isbn.orEmpty(),
                    publisher = w.publisher.orEmpty(),
                    publishedYear = w.publishedYear?.toString().orEmpty(),
                    notes = w.notes.orEmpty(),
                    priceEstimate = w.priceEstimate?.toString().orEmpty(),
                    priority = w.priority,
                    coverUrl = w.coverUrl,
                )
            }
        }
    }

    fun onTitleChange(v: String) = _form.update { it.copy(title = v, titleError = false) }
    fun onTransliterationChange(v: String) = _form.update { it.copy(titleTransliteration = v) }
    fun onAuthorChange(v: String) = _form.update { it.copy(author = v) }
    fun onIsbnChange(v: String) = _form.update { it.copy(isbn = v) }
    fun onPublisherChange(v: String) = _form.update { it.copy(publisher = v) }
    fun onYearChange(v: String) = _form.update { it.copy(publishedYear = v) }
    fun onPriceChange(v: String) = _form.update { it.copy(priceEstimate = v) }
    fun onPriorityChange(v: Int) = _form.update { it.copy(priority = v) }
    fun onNotesChange(v: String) = _form.update { it.copy(notes = v) }

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
                is LookupResult.NotFound -> _events.trySend(AddWishlistEvent.LookupNotFound)
                is LookupResult.NetworkError -> _events.trySend(AddWishlistEvent.LookupNetworkError)
            }
        }
    }

    fun onSave() {
        val current = _form.value
        if (current.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        if (current.mode == AddWishlistMode.Edit) {
            val original = originalBook ?: return
            viewModelScope.launch {
                val updated = current.toWishlistBook().copy(
                    id = original.id,
                    addedDate = original.addedDate,
                    purchased = original.purchased,
                    purchasedDate = original.purchasedDate,
                    needsMetadata = original.needsMetadata,
                    source = original.source,
                )
                when (updateBook(updated)) {
                    UpdateWishlistBookUseCase.Result.Success -> _events.trySend(AddWishlistEvent.Saved(original.id))
                    UpdateWishlistBookUseCase.Result.NotFound -> _events.trySend(AddWishlistEvent.NotFound)
                    UpdateWishlistBookUseCase.Result.TitleRequired ->
                        _form.update { it.copy(titleError = true) }
                    UpdateWishlistBookUseCase.Result.IdImmutable,
                    UpdateWishlistBookUseCase.Result.AddedDateImmutable ->
                        _events.trySend(AddWishlistEvent.NotFound)
                }
            }
            return
        }
        viewModelScope.launch {
            val normalized = LookupIsbnUseCase.normalize(current.isbn)
            if (normalized.isNotEmpty()) {
                val existing = repository.findByIsbn(normalized)
                if (existing != null) {
                    _events.trySend(AddWishlistEvent.DuplicateIsbn(existing.id))
                    return@launch
                }
            }
            when (val r = addBook(current.toWishlistBook())) {
                is AddWishlistBookUseCase.Result.Success -> _events.trySend(AddWishlistEvent.Saved(r.id))
                AddWishlistBookUseCase.Result.TitleRequired ->
                    _form.update { it.copy(titleError = true) }
            }
        }
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
                coverUrl = metadata.coverUrl,
                titleError = false,
            )
        }
    }
}

data class AddWishlistFormState(
    val mode: AddWishlistMode = AddWishlistMode.Add,
    val title: String = "",
    val titleTransliteration: String = "",
    val author: String = "",
    val isbn: String = "",
    val publisher: String = "",
    val publishedYear: String = "",
    val priceEstimate: String = "",
    val priority: Int = WishlistBook.PRIORITY_MED,
    val notes: String = "",
    val coverUrl: String? = null,
    val titleError: Boolean = false,
    val isLookingUp: Boolean = false,
) {
    fun toWishlistBook(): WishlistBook = WishlistBook(
        title = title.trim(),
        titleTransliteration = titleTransliteration.trim().ifBlank { null },
        author = author.trim().ifBlank { null },
        isbn = isbn.trim().ifBlank { null },
        publisher = publisher.trim().ifBlank { null },
        publishedYear = publishedYear.trim().toIntOrNull(),
        priceEstimate = priceEstimate.trim().toDoubleOrNull(),
        priority = priority,
        notes = notes.trim().ifBlank { null },
        coverUrl = coverUrl,
        source = WishlistBook.Source.MANUAL,
    )
}

enum class AddWishlistMode { Add, Edit }

sealed interface AddWishlistEvent {
    data class Saved(val id: Long) : AddWishlistEvent
    data class DuplicateIsbn(val existingId: Long) : AddWishlistEvent
    data object NotFound : AddWishlistEvent
    data object LookupNotFound : AddWishlistEvent
    data object LookupNetworkError : AddWishlistEvent
}
