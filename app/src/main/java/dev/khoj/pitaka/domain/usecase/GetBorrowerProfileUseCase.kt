package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.BorrowerStats
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * D31: borrower profile = name + contact + notes + currently borrowed + full
 * returned history + live-computed stats.
 *
 * The stats numbers come from a single aggregation query (LoanDao.statsForBorrower),
 * not from re-walking the loans list. This keeps the screen snappy even for
 * borrowers with hundreds of loans.
 */
class GetBorrowerProfileUseCase @Inject constructor(
    private val borrowers: BorrowerRepository,
    private val loans: LoanRepository,
) {
    data class Profile(
        val borrower: Borrower,
        val active: List<Loan>,
        val returned: List<Loan>,
        val stats: BorrowerStats,
    )

    sealed interface Result {
        data class Success(val profile: Profile) : Result
        data object NotFound : Result
        data object VaultLocked : Result
    }

    suspend operator fun invoke(
        borrowerId: Long,
        now: Long = System.currentTimeMillis(),
        allLoans: List<Loan>,
    ): Result {
        return try {
            val borrower = borrowers.getById(borrowerId) ?: return Result.NotFound
            val active = allLoans.filter { !it.isReturned }
            val returned = allLoans.filter { it.isReturned }
                .sortedByDescending { it.returnedDate ?: 0L }
            val stats = loans.statsFor(borrowerId, now)
            Result.Success(Profile(borrower, active, returned, stats))
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        }
    }
}
