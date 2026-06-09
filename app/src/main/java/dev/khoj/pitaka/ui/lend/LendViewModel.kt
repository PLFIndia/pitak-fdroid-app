package dev.khoj.pitaka.ui.lend

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.usecase.LendBookUseCase
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LendViewModel @Inject constructor(
    books: BookRepository,
    borrowers: BorrowerRepository,
    private val lendBook: LendBookUseCase,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _form = MutableStateFlow(LendFormState())
    val form: StateFlow<LendFormState> = combine(
        _form,
        books.observeById(bookId),
        borrowers.observeAll(),
    ) { f, book, borrowerList ->
        f.copy(
            book = book,
            borrowers = borrowerList,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LendFormState(),
    )

    private val _events = Channel<LendEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onPickExisting(id: Long?) = _form.update { it.copy(selectedBorrowerId = id) }
    fun onNewNameChange(v: String) = _form.update { it.copy(newBorrowerName = v) }
    fun onNewContactChange(v: String) = _form.update { it.copy(newBorrowerContact = v) }
    fun onDueDateChange(epoch: Long?) = _form.update { it.copy(dueDate = epoch) }
    fun onNotesChange(v: String) = _form.update { it.copy(notes = v) }

    fun onSave() {
        val current = _form.value
        viewModelScope.launch {
            val r = lendBook(
                bookId = bookId,
                existingBorrowerId = current.selectedBorrowerId,
                newBorrowerName = current.newBorrowerName.takeIf { it.isNotBlank() },
                newBorrowerContact = current.newBorrowerContact.takeIf { it.isNotBlank() },
                dueDate = current.dueDate,
                notes = current.notes.takeIf { it.isNotBlank() },
            )
            when (r) {
                is LendBookUseCase.Result.Success -> _events.trySend(LendEvent.Lent(r.loanId))
                LendBookUseCase.Result.BorrowerRequired -> _events.trySend(LendEvent.BorrowerRequired)
                LendBookUseCase.Result.BookRequired -> _events.trySend(LendEvent.Error(context.getString(R.string.lend_error_book_missing)))
                LendBookUseCase.Result.VaultLocked -> _events.trySend(LendEvent.VaultLocked)
                is LendBookUseCase.Result.Failed ->
                    _events.trySend(LendEvent.Error(r.cause.message ?: context.getString(R.string.error_generic)))
            }
        }
    }
}

data class LendFormState(
    val book: Book? = null,
    val borrowers: List<Borrower> = emptyList(),
    val selectedBorrowerId: Long? = null,
    val newBorrowerName: String = "",
    val newBorrowerContact: String = "",
    val dueDate: Long? = null,
    val notes: String = "",
)

sealed interface LendEvent {
    data class Lent(val loanId: Long) : LendEvent
    data object BorrowerRequired : LendEvent
    data object VaultLocked : LendEvent
    data class Error(val message: String) : LendEvent
}
