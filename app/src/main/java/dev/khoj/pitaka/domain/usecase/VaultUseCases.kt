package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.crypto.KeystoreVault
import dev.khoj.pitaka.data.crypto.VaultPassphrase
import dev.khoj.pitaka.data.vault.VaultSession
import javax.inject.Inject

/**
 * D18: one-tap vault enable. Generates the daily passphrase, wraps it via
 * Keystore, and immediately unlocks the session so the caller can keep
 * working.
 */
class EnableVaultUseCase @Inject constructor(
    private val keystore: KeystoreVault,
    private val session: VaultSession,
) {
    sealed interface Result {
        data object Success : Result
        data object AlreadyEnabled : Result
        data class Failed(val cause: Throwable) : Result
    }

    operator fun invoke(): Result {
        if (keystore.isInitialized()) return Result.AlreadyEnabled
        return try {
            val passphrase: VaultPassphrase = keystore.initializeAndWrap()
            session.unlock(passphrase) // consumes the passphrase
            Result.Success
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }
}

/** Unlocks the vault using the stored Keystore-wrapped passphrase. */
class UnlockVaultUseCase @Inject constructor(
    private val keystore: KeystoreVault,
    private val session: VaultSession,
) {
    sealed interface Result {
        data object Success : Result
        data object NotInitialized : Result
        data class Failed(val cause: Throwable) : Result
    }

    /**
     * @param ticket proof the caller came through the authenticated unlock
     * path (F-06 software guard) — mint it via [KeystoreVault.authorizeUnlock]
     * inside the BiometricPrompt success callback.
     */
    operator fun invoke(ticket: KeystoreVault.UnlockTicket): Result {
        if (!keystore.isInitialized()) return Result.NotInitialized
        return try {
            val passphrase = keystore.unwrap(ticket)
                ?: return Result.Failed(IllegalStateException("Wrapped passphrase missing"))
            session.unlock(passphrase)
            Result.Success
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }
}

class LockVaultUseCase @Inject constructor(private val session: VaultSession) {
    operator fun invoke() { session.lock() }
}
