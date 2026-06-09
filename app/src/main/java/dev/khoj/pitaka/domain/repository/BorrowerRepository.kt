package dev.khoj.pitaka.domain.repository

import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.BorrowerStats
import dev.khoj.pitaka.domain.model.Loan
import kotlinx.coroutines.flow.Flow

/**
 * Vault-gated repositories. Every method requires the vault to be unlocked;
 * implementations throw [dev.khoj.pitaka.data.vault.VaultLockedException]
 * otherwise. UseCases convert that to a domain-level result.
 */
interface BorrowerRepository {
    fun observeAll(): Flow<List<Borrower>>
    fun search(query: String): Flow<List<Borrower>>
    suspend fun getById(id: Long): Borrower?
    suspend fun findByName(name: String): Borrower?
    suspend fun upsert(borrower: Borrower): Long
    suspend fun delete(id: Long)
}

interface LoanRepository {
    fun observeActive(): Flow<List<Loan>>
    fun observeReturned(): Flow<List<Loan>>
    fun observeForBorrower(borrowerId: Long): Flow<List<Loan>>
    fun observeOverdue(now: Long): Flow<List<Loan>>
    fun observeDueSoon(now: Long, withinMs: Long): Flow<List<Loan>>

    suspend fun getById(id: Long): Loan?
    suspend fun getAllForBook(bookId: Long): List<Loan>
    suspend fun upsert(loan: Loan): Long
    suspend fun delete(id: Long)
    suspend fun deleteAllForBook(bookId: Long): Int
    suspend fun countForBook(bookId: Long): Int

    /**
     * Reactive count of ACTIVE loans for a book, or null when the vault is
     * locked (the count itself is vault-derived; emitting it while locked would
     * leak loan state per D4 / §1.1). The detail screen shows Available only
     * when this is non-null.
     */
    fun observeActiveCountForBook(bookId: Long): Flow<Int?>

    /**
     * Reactive map of bookId → active (not-yet-returned) loan count, covering
     * only books that currently have at least one active loan. Null when the
     * vault is locked. Same D4 / §1.1 rule as [observeActiveCountForBook]:
     * lent-state is vault-derived, so the Library list shows an availability
     * badge only when this is non-null (vault unlocked); null while locked means
     * the list shows no availability info. Counts (not just IDs) so a multi-copy
     * book is "not available" only when every copy is out.
     */
    fun observeActiveLoanCountsByBook(): Flow<Map<Long, Int>?>

    suspend fun statsFor(borrowerId: Long, now: Long): BorrowerStats
}
