package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.crypto.BackupPassphraseWrapper
import dev.khoj.pitaka.data.crypto.KeystoreVault
import dev.khoj.pitaka.data.vault.VaultSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * D5 + D18: set or change the backup passphrase. Requires the vault to be
 * unlocked (so we have the daily passphrase to wrap).
 */
class SetBackupPassphraseUseCase @Inject constructor(
    private val keystore: KeystoreVault,
    private val session: VaultSession,
    private val backupWrapper: BackupPassphraseWrapper,
) {
    sealed interface Result {
        data object Success : Result
        data object VaultLocked : Result
        data object PassphraseTooShort : Result
        data class Failed(val cause: Throwable) : Result
    }

    operator fun invoke(passphrase: CharArray, hint: String?): Result {
        if (passphrase.size < MIN_PASSPHRASE_LEN) return Result.PassphraseTooShort
        if (!session.isUnlocked()) return Result.VaultLocked
        return try {
            // Unwrap the live passphrase from Keystore so we can re-wrap it
            // under the user-supplied backup passphrase. The vault is already
            // unlocked here (checked above) — that is the authenticated context
            // F-06's ticket represents, so we mint one inline.
            val daily = keystore.unwrap(keystore.authorizeUnlock())
                ?: return Result.Failed(IllegalStateException("Vault not initialized"))
            try {
                backupWrapper.setBackupPassphrase(daily, passphrase, hint)
                Result.Success
            } finally {
                daily.close()
            }
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    companion object {
        const val MIN_PASSPHRASE_LEN = 8
    }
}

/**
 * D19: wipe the vault, start fresh. The user's on-device live data is
 * preserved (Library + Wishlist live in unencrypted DBs). The vault
 * (borrowers + loans) and both passphrases (daily + backup) are destroyed
 * irreversibly.
 */
class WipeVaultAndStartFreshUseCase @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val keystore: KeystoreVault,
    private val backupWrapper: BackupPassphraseWrapper,
    private val session: VaultSession,
) {
    operator fun invoke() {
        session.lock()
        keystore.wipeAndReset()
        backupWrapper.clear()
        // The encrypted SQLCipher DB file is now unreadable (key is gone).
        // Delete it so the next enable creates a fresh blank DB.
        runCatching {
            context.getDatabasePath(
                dev.khoj.pitaka.data.local.borrowers.BorrowersDatabase.NAME
            ).delete()
        }
    }
}
