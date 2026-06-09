package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * D25: lend a book.
 *
 * Required: book + borrower (existing OR a non-blank name to create inline)
 * + lent date (defaults to now).
 * Optional: due date, notes (loan-level), borrower contact when creating
 * a new borrower row.
 */
class LendBookUseCase @Inject constructor(
    private val borrowers: BorrowerRepository,
    private val loans: LoanRepository,
) {
    sealed interface Result {
        data class Success(val loanId: Long, val borrowerId: Long) : Result
        data object BookRequired : Result
        data object BorrowerRequired : Result
        data object VaultLocked : Result
        data class Failed(val cause: Throwable) : Result
    }

    suspend operator fun invoke(
        bookId: Long,
        existingBorrowerId: Long? = null,
        newBorrowerName: String? = null,
        newBorrowerContact: String? = null,
        lentDate: Long = System.currentTimeMillis(),
        dueDate: Long? = null,
        notes: String? = null,
    ): Result {
        if (bookId <= 0L) return Result.BookRequired
        return try {
            val borrowerId = when {
                existingBorrowerId != null && existingBorrowerId > 0L -> existingBorrowerId
                !newBorrowerName.isNullOrBlank() -> {
                    val existing = borrowers.findByName(newBorrowerName.trim())
                    existing?.id ?: borrowers.upsert(
                        Borrower(
                            name = newBorrowerName.trim(),
                            contact = newBorrowerContact?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    )
                }
                else -> return Result.BorrowerRequired
            }
            val loanId = loans.upsert(
                Loan(
                    bookId = bookId,
                    borrowerId = borrowerId,
                    lentDate = lentDate,
                    dueDate = dueDate,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
            Result.Success(loanId = loanId, borrowerId = borrowerId)
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }
}

class ReturnLoanUseCase @Inject constructor(
    private val loans: LoanRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data object VaultLocked : Result
    }

    suspend operator fun invoke(loanId: Long, returnedAt: Long = System.currentTimeMillis()): Result {
        return try {
            val loan = loans.getById(loanId) ?: return Result.NotFound
            loans.upsert(loan.copy(returnedDate = returnedAt))
            Result.Success
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        }
    }
}
