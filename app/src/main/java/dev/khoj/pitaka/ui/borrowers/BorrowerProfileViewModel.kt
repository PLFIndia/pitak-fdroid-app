package dev.khoj.pitaka.ui.borrowers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.BorrowerStats
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.ui.nav.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BorrowerProfileViewModel @Inject constructor(
    private val borrowers: BorrowerRepository,
    private val loans: LoanRepository,
    private val books: BookRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val borrowerId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    val state: StateFlow<BorrowerProfileUiState> =
        loans.observeForBorrower(borrowerId).flatMapLatest { allLoans ->
            // Resolve only the books this borrower's loans reference — not the
            // whole library (P5). distinct ids keeps the IN (...) list tight.
            val ids = allLoans.map { it.bookId }.distinct()
            books.observeByIds(ids).map { bookList ->
                val bookMap = bookList.associateBy { it.id }
                val active = allLoans.filter { !it.isReturned }
                val returned = allLoans.filter { it.isReturned }
                    .sortedByDescending { it.returnedDate ?: 0L }
                val now = System.currentTimeMillis()
                val borrower = borrowers.getById(borrowerId)
                val stats = if (borrower != null) loans.statsFor(borrowerId, now) else null

                BorrowerProfileUiState(
                    borrower = borrower,
                    active = active.map { it to bookMap[it.bookId] },
                    returned = returned.map { it to bookMap[it.bookId] },
                    stats = stats,
                    isLoading = false,
                    notFound = borrower == null,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BorrowerProfileUiState(),
        )
}

data class BorrowerProfileUiState(
    val borrower: Borrower? = null,
    val active: List<Pair<Loan, Book?>> = emptyList(),
    val returned: List<Pair<Loan, Book?>> = emptyList(),
    val stats: BorrowerStats? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)
