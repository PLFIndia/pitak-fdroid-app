package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.crypto.BackupPassphraseWrapper
import dev.khoj.pitaka.data.vault.VaultPreferences
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.data.vault.VaultSession
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * D26b: consolidated reminders. Pending screen is vault-gated.
 *
 * Items surfaced (when unlocked):
 *  - overdue loans
 *  - loans due soon (within `dueSoonWithinDays`)
 *  - books with `needsMetadata` (cross-DB: from books.db)
 *  - backup-passphrase nudge (if banner not acknowledged and no backup set)
 *  - backup-staleness nudge (Phase 7: when last-backup age > threshold)
 */
class GetPendingUseCase @Inject constructor(
    private val books: BookRepository,
    private val loans: LoanRepository,
    private val backupWrapper: BackupPassphraseWrapper,
    private val session: VaultSession,
    private val vaultPrefs: VaultPreferences,
) {
    data class Snapshot(
        val overdue: List<Loan>,
        val dueSoon: List<Loan>,
        val staleMetadataBooks: List<Book>,
        val backupPassphraseNeeded: Boolean,
        val backupStaleDays: Int?,
    ) {
        val isEmpty: Boolean
            get() = overdue.isEmpty() &&
                    dueSoon.isEmpty() &&
                    staleMetadataBooks.isEmpty() &&
                    !backupPassphraseNeeded &&
                    backupStaleDays == null
    }

    sealed interface Result {
        data class Success(val snapshot: Snapshot) : Result
        data object VaultLocked : Result
    }

    suspend operator fun invoke(
        now: Long = System.currentTimeMillis(),
        dueSoonWithinDays: Int = 3,
    ): Result {
        if (!session.isUnlocked()) return Result.VaultLocked
        return try {
            val withinMs = dueSoonWithinDays.toLong() * 24 * 60 * 60 * 1000
            val overdue = loans.observeOverdue(now).first()
            val dueSoon = loans.observeDueSoon(now, withinMs).first()
            val stale = books.observeNeedsMetadata().first()
            val backupNeeded = !backupWrapper.isSet()
            val lastBackup = vaultPrefs.lastBackupAtMs()
            val staleThresholdMs = vaultPrefs.backupStalenessThresholdMs()
            val staleDays: Int? = if (lastBackup == 0L) {
                null // never backed up — covered by backupPassphraseNeeded / Backup screen
            } else {
                val age = now - lastBackup
                if (age > staleThresholdMs) (age / (24L * 60 * 60 * 1000)).toInt() else null
            }
            Result.Success(
                Snapshot(
                    overdue = overdue,
                    dueSoon = dueSoon,
                    staleMetadataBooks = stale,
                    backupPassphraseNeeded = backupNeeded,
                    backupStaleDays = staleDays,
                )
            )
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        }
    }
}
