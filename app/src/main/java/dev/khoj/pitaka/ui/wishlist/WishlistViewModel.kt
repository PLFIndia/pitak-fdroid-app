package dev.khoj.pitaka.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import dev.khoj.pitaka.domain.repository.WishlistSort
import dev.khoj.pitaka.domain.usecase.DeleteWishlistBookUseCase
import dev.khoj.pitaka.domain.usecase.MarkWishlistPurchasedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
    private val deleteBook: DeleteWishlistBookUseCase,
    private val markPurchased: MarkWishlistPurchasedUseCase,
    private val prefs: dev.khoj.pitaka.data.prefs.AppPreferences,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val books = combine(
        query,
        prefs.wishlistFilter(),
        prefs.wishlistSort(),
    ) { q, filter, sort -> Triple(q, filter, sort) }
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { (q, filter, sort) ->
            when {
                q.isNotBlank()                  -> repository.search(q)
                filter == WishlistFilter.Purchased -> repository.observePurchased()
                else                            -> repository.observeActive(sort)
            }
        }

    val state: StateFlow<WishlistUiState> = combine(
        books,
        query,
        prefs.wishlistFilter(),
        prefs.wishlistSort(),
    ) { list, q, filter, sort ->
        WishlistUiState(books = list, query = q, filter = filter, sort = sort, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WishlistUiState(),
    )

    private val _events = Channel<WishlistEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onQueryChange(v: String) { query.value = v }
    fun onFilterChange(v: WishlistFilter) {
        viewModelScope.launch { prefs.setWishlistFilter(v) }
    }
    fun onSortChange(v: dev.khoj.pitaka.domain.repository.WishlistSort) {
        viewModelScope.launch { prefs.setWishlistSort(v) }
    }

    fun onDeleteBook(id: Long) {
        viewModelScope.launch { deleteBook(id) }
    }

    fun onMarkPurchased(id: Long, moveToLibrary: Boolean) {
        viewModelScope.launch {
            when (val r = markPurchased(wishlistBookId = id, moveToLibrary = moveToLibrary)) {
                MarkWishlistPurchasedUseCase.Result.Success -> {
                    _events.trySend(WishlistEvent.Purchased(id = id, moved = moveToLibrary))
                }
                MarkWishlistPurchasedUseCase.Result.NotFound -> Unit
                is MarkWishlistPurchasedUseCase.Result.AlreadyInLibrary -> {
                    _events.trySend(WishlistEvent.AlreadyInLibrary(existingBookId = r.existingBookId))
                }
            }
        }
    }
}

data class WishlistUiState(
    val books: List<WishlistBook> = emptyList(),
    val query: String = "",
    val filter: WishlistFilter = WishlistFilter.Active,
    val sort: dev.khoj.pitaka.domain.repository.WishlistSort = dev.khoj.pitaka.domain.repository.WishlistSort.Priority,
    val isLoading: Boolean = true,
)

enum class WishlistFilter { Active, Purchased }

sealed interface WishlistEvent {
    data class Purchased(val id: Long, val moved: Boolean) : WishlistEvent
    data class AlreadyInLibrary(val existingBookId: Long) : WishlistEvent
}
