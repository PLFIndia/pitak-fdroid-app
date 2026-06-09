@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.khoj.pitaka.data.repository

import dev.khoj.pitaka.data.local.borrowers.toDomain
import dev.khoj.pitaka.data.local.borrowers.toEntity
import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.data.vault.VaultSession
import dev.khoj.pitaka.data.vault.VaultState
import dev.khoj.pitaka.domain.model.BorrowerStats
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepositoryImpl @Inject constructor(
    private val session: VaultSession,
) : LoanRepository {

    override fun observeActive(): Flow<List<Loan>> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database ?: return@flatMapLatest flowOf(emptyList())
        db.loanDao().observeActive().map { list -> list.map { it.toDomain() } }
    }

    override fun observeReturned(): Flow<List<Loan>> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database ?: return@flatMapLatest flowOf(emptyList())
        db.loanDao().observeReturned().map { list -> list.map { it.toDomain() } }
    }

    override fun observeForBorrower(borrowerId: Long): Flow<List<Loan>> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database ?: return@flatMapLatest flowOf(emptyList())
        db.loanDao().observeForBorrower(borrowerId).map { list -> list.map { it.toDomain() } }
    }

    override fun observeOverdue(now: Long): Flow<List<Loan>> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database ?: return@flatMapLatest flowOf(emptyList())
        db.loanDao().observeOverdue(now).map { list -> list.map { it.toDomain() } }
    }

    override fun observeDueSoon(now: Long, withinMs: Long): Flow<List<Loan>> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database ?: return@flatMapLatest flowOf(emptyList())
        db.loanDao().observeDueSoon(now, now + withinMs).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: Long): Loan? = unlockedDao().getById(id)?.toDomain()

    override suspend fun getAllForBook(bookId: Long): List<Loan> =
        unlockedDao().getAllForBook(bookId).map { it.toDomain() }

    override suspend fun upsert(loan: Loan): Long = unlockedDao().upsert(loan.toEntity())

    override suspend fun delete(id: Long) {
        unlockedDao().deleteById(id)
    }

    override suspend fun deleteAllForBook(bookId: Long): Int =
        unlockedDao().deleteAllForBook(bookId)

    override suspend fun countForBook(bookId: Long): Int =
        unlockedDao().countForBook(bookId)

    override fun observeActiveCountForBook(bookId: Long): Flow<Int?> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database
            // Vault locked → emit null so the UI hides Available (D4: a count
            // would leak loan state). Unlocked → the live active-loan count.
            ?: return@flatMapLatest flowOf(null)
        db.loanDao().observeActiveCountForBook(bookId)
    }

    override fun observeActiveLoanCountsByBook(): Flow<Map<Long, Int>?> = session.state.flatMapLatest { s ->
        val db = (s as? VaultState.Unlocked)?.database
            // Vault locked → null so the Library list shows no availability info
            // (D4: lent-state is vault-derived). Unlocked → bookId → active count.
            ?: return@flatMapLatest flowOf(null)
        db.loanDao().observeActive().map { list ->
            list.groupingBy { it.bookId }.eachCount()
        }
    }

    override suspend fun statsFor(borrowerId: Long, now: Long): BorrowerStats {
        val row = unlockedDao().statsForBorrower(borrowerId, now)
        val avgDays: Double? = if (row.returnedCount > 0) {
            val avgMs = row.totalReturnedDurationMs.toDouble() / row.returnedCount
            avgMs / (1000.0 * 60 * 60 * 24)
        } else null
        val overdueRate: Double = if (row.totalLoans > 0) {
            row.overdueCount.toDouble() / row.totalLoans
        } else 0.0
        return BorrowerStats(
            totalLoans = row.totalLoans,
            averageReturnDays = avgDays,
            overdueRate = overdueRate,
        )
    }

    private fun unlockedDao() =
        session.currentDatabase()?.loanDao() ?: throw VaultLockedException()
}
