package dev.khoj.pitaka.data.vault

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.crypto.VaultPassphrase
import dev.khoj.pitaka.data.local.borrowers.BorrowersDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the open [BorrowersDatabase] when the vault is unlocked, nothing
 * when locked. Single source of truth for vault state across the app.
 *
 * Per Decision 1 (option A), unlock is gated by app-level biometric in the
 * UI layer; this class only handles the cryptographic side.
 *
 * Thread-safety: all mutations are guarded by the class monitor. Reads of
 * [state] are via `StateFlow`, safe by definition.
 */
@Singleton
class VaultSession @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _state = MutableStateFlow<VaultState>(VaultState.Locked)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /** Convenience: the open DB if unlocked, null otherwise. */
    fun currentDatabase(): BorrowersDatabase? =
        (_state.value as? VaultState.Unlocked)?.database

    fun isUnlocked(): Boolean = _state.value is VaultState.Unlocked

    /**
     * Opens the SQLCipher-wrapped Room DB with [passphrase] and transitions
     * to Unlocked. The passphrase is consumed (zeroed) by SQLCipher's
     * SupportFactory; callers must not reuse the same VaultPassphrase
     * instance.
     */
    @Synchronized
    fun unlock(passphrase: VaultPassphrase) {
        // Already unlocked → close existing DB first to avoid leaks.
        (_state.value as? VaultState.Unlocked)?.database?.close()

        val factory = SupportOpenHelperFactory(passphrase.copyBytes())
        val database = Room.databaseBuilder(
            context,
            BorrowersDatabase::class.java,
            BorrowersDatabase.NAME,
        )
            .openHelperFactory(factory)
            .build()
        // Touch the database to force-open + verify the passphrase is correct.
        // SQLCipher errors arrive only when we actually hit SQLite.
        try {
            database.openHelper.writableDatabase.version
        } catch (t: Throwable) {
            database.close()
            throw VaultUnlockException("Failed to open SQLCipher database", t)
        } finally {
            passphrase.close()
        }
        _state.value = VaultState.Unlocked(database)
    }

    @Synchronized
    fun lock() {
        val current = _state.value
        if (current is VaultState.Unlocked) {
            current.database.close()
        }
        _state.value = VaultState.Locked
    }
}

sealed interface VaultState {
    data object Locked : VaultState
    data class Unlocked(val database: BorrowersDatabase) : VaultState
}

class VaultUnlockException(message: String, cause: Throwable) : RuntimeException(message, cause)

class VaultLockedException : RuntimeException("Vault is locked")
