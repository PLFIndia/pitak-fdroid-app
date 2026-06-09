package dev.khoj.pitaka.data.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Argon2id-backed [PinHasher]. Same KDF + parameters as the backup passphrase
 * wrapper, so the PIN is hashed with a memory-hard function that resists
 * brute-forcing even though a PIN has low entropy.
 */
@Singleton
class Argon2PinHasher @Inject constructor() : PinHasher {

    override fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        val pwBytes = String(pin).toByteArray(Charsets.UTF_8)
        try {
            val result = Argon2Kt().hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pwBytes,
                salt = salt,
                tCostInIterations = T_COST,
                mCostInKibibyte = M_COST_KIB,
                parallelism = PARALLELISM,
                hashLengthInBytes = HASH_BYTES,
            )
            return result.rawHashAsByteArray()
        } finally {
            java.util.Arrays.fill(pwBytes, 0.toByte())
        }
    }

    companion object {
        // Match BackupPassphraseWrapper's tuning.
        private const val T_COST = 3
        private const val M_COST_KIB = 65_536 // 64MB
        private const val PARALLELISM = 1
        private const val HASH_BYTES = 32
    }
}
