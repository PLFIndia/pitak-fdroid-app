package dev.khoj.pitaka.ui.wishlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import dev.khoj.pitaka.domain.usecase.DeleteWishlistBookUseCase
import dev.khoj.pitaka.domain.usecase.MarkWishlistPurchasedUseCase
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistDetailViewModel @Inject constructor(
    private val repository: WishlistRepository,
    private val deleteBook: DeleteWishlistBookUseCase,
    private val markPurchased: MarkWishlistPurchasedUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _state = MutableStateFlow(WishlistDetailUiState())
    val state: StateFlow<WishlistDetailUiState> = _state.asStateFlow()

    private val _events = Channel<WishlistDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val book = repository.getById(id)
            _state.value = WishlistDetailUiState(book = book, isLoading = false, notFound = book == null)
        }
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            deleteBook(id)
            _events.trySend(WishlistDetailEvent.Deleted)
        }
    }

    fun onMarkPurchased(moveToLibrary: Boolean) {
        viewModelScope.launch {
            when (val r = markPurchased(wishlistBookId = id, moveToLibrary = moveToLibrary)) {
                MarkWishlistPurchasedUseCase.Result.Success -> {
                    _events.trySend(WishlistDetailEvent.Purchased)
                }
                MarkWishlistPurchasedUseCase.Result.NotFound -> Unit
                is MarkWishlistPurchasedUseCase.Result.AlreadyInLibrary ->
                    _events.trySend(WishlistDetailEvent.AlreadyInLibrary(r.existingBookId))
            }
        }
    }
}

data class WishlistDetailUiState(
    val book: WishlistBook? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)

sealed interface WishlistDetailEvent {
    data object Deleted : WishlistDetailEvent
    data object Purchased : WishlistDetailEvent
    data class AlreadyInLibrary(val bookId: Long) : WishlistDetailEvent
}
