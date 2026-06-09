package dev.khoj.pitaka.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.domain.usecase.ReturnLoanUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loans: LoanRepository,
    private val borrowers: BorrowerRepository,
    private val books: BookRepository,
    private val returnLoan: ReturnLoanUseCase,
) : ViewModel() {

    val state: StateFlow<LoansUiState> = combine(
        loans.observeActive(),
        loans.observeReturned(),
        borrowers.observeAll(),
        books.observeAll(),
    ) { active, returned, borrowerList, bookList ->
        val borrowerMap = borrowerList.associateBy { it.id }
        val bookMap = bookList.associateBy { it.id }
        LoansUiState(
            active = active.map { it.withResolved(borrowerMap, bookMap) },
            returned = returned.map { it.withResolved(borrowerMap, bookMap) },
            borrowers = borrowerList,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LoansUiState(),
    )

    fun onReturnLoan(loanId: Long) {
        viewModelScope.launch { returnLoan(loanId) }
    }

    private fun Loan.withResolved(
        borrowerMap: Map<Long, Borrower>,
        bookMap: Map<Long, dev.khoj.pitaka.domain.model.Book>,
    ) = ResolvedLoan(
        loan = this,
        borrowerName = borrowerMap[borrowerId]?.name ?: "(unknown)",
        bookTitle = bookMap[bookId]?.title ?: "(book removed)",
    )
}

data class ResolvedLoan(
    val loan: Loan,
    val borrowerName: String,
    val bookTitle: String,
)

data class LoansUiState(
    val active: List<ResolvedLoan> = emptyList(),
    val returned: List<ResolvedLoan> = emptyList(),
    val borrowers: List<Borrower> = emptyList(),
    val isLoading: Boolean = true,
)
