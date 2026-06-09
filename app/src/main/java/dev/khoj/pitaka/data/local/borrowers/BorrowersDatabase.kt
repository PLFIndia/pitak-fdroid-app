package dev.khoj.pitaka.data.local.borrowers

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Encrypted vault database (pitaka.md §4.1.C). Wrapped by SQLCipher.
 *
 * Versions:
 *   v1 (Phase 4) — initial schema.
 *
 * Construction lives in [dev.khoj.pitaka.data.vault.VaultSession], which
 * holds the unwrapped passphrase only while unlocked. We deliberately do
 * NOT provide this DB as a Hilt singleton — it must be open/close-able
 * on demand.
 */
@Database(
    entities = [BorrowerEntity::class, LoanEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BorrowersDatabase : RoomDatabase() {
    abstract fun borrowerDao(): BorrowerDao
    abstract fun loanDao(): LoanDao

    companion object {
        const val NAME = "borrowers.db"
    }
}
