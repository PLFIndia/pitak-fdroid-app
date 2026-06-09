package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.data.vault.VaultSession
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * Delete a book by id.
 *
 * D3: if the book has loan history in the vault, the vault must be unlocked
 * before delete. When unlocked, the book's loan rows are also purged (no
 * cross-DB FK, the repo enforces integrity).
 *
 * Return values let the UI route correctly:
 *  - [Result.Success] — book deleted (and loans purged if any existed).
 *  - [Result.RequiresVaultUnlock] — there are loans; the vault is locked.
 *    The UI prompts unlock and re-invokes.
 *  - [Result.VaultLocked] — vault was locked DURING the loan check; the
 *    caller should retry after unlock.
 */
class DeleteBookUseCase @Inject constructor(
    private val books: BookRepository,
    private val loans: LoanRepository,
    private val session: VaultSession,
) {
    sealed interface Result {
        data object Success : Result
        data object RequiresVaultUnlock : Result
        data object VaultLocked : Result
    }

    suspend operator fun invoke(id: Long): Result = try {
        if (session.isUnlocked()) {
            // Try to purge loan rows for this book; ignore count.
            runCatching { loans.deleteAllForBook(id) }
            books.delete(id)
            Result.Success
        } else {
            // We need to check if this book has any loan history. The only way
            // to do that is by opening the vault. So we surface a request to
            // unlock; the screen flow shows biometric, then re-invokes.
            // (We avoid a cheap "loanCountForBook(id)" optimization that
            // would require a parallel unencrypted index — that would leak
            // vault state per §1.1.)
            Result.RequiresVaultUnlock
        }
    } catch (e: VaultLockedException) {
        Result.VaultLocked
    }
}
