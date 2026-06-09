package dev.khoj.pitaka.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App Lock core logic: PIN set/verify, biometric opt-in, and the wrong-PIN
 * lockout throttle. Independent of the SQLCipher vault — App Lock gates the UI,
 * it does not encrypt anything (decision A in PLAN.md).
 *
 * PIN is never stored in plaintext: we keep `Argon2id(pin + random salt)` plus
 * the salt (decision D-applock-3). Verification re-hashes the entered PIN with
 * the stored salt and compares in constant time.
 *
 * Lockout (L1): after [MAX_ATTEMPTS] consecutive wrong PINs, entry is throttled
 * for an escalating delay; never wipes data.
 *
 * All persistence + hashing go through injected seams ([AppLockStore],
 * [PinHasher]) so this logic is unit-testable without Android/Argon2.
 */
@Singleton
class AppLockManager(
    private val store: AppLockStore,
    private val hasher: PinHasher,
    private val clock: () -> Long,
    private val random: SecureRandom,
) {
    @Inject
    constructor(
        store: AppLockStore,
        hasher: PinHasher,
    ) : this(store, hasher, clock = System::currentTimeMillis, random = SecureRandom())

    /** App Lock is enabled iff a PIN hash is stored. */
    fun isEnabled(): Boolean = store.pinHash != null && store.pinSalt != null

    fun isBiometricEnabled(): Boolean = isEnabled() && store.biometricEnabled

    fun autoLockTimeoutMs(): Long =
        store.autoLockTimeoutMs.takeIf { it > 0 } ?: DEFAULT_AUTOLOCK_MS

    fun setAutoLockTimeoutMs(ms: Long) {
        store.autoLockTimeoutMs = ms
    }

    /**
     * Sets (or replaces) the PIN. Returns false if the PIN is too short.
     * Clears any throttle/attempt state. Caller should zero [pin] after.
     */
    fun setPin(pin: CharArray): Boolean {
        if (pin.size < MIN_PIN_LENGTH) return false
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = hasher.hash(pin, salt)
        store.pinSalt = encode(salt)
        store.pinHash = encode(hash)
        store.failedAttempts = 0
        store.lockoutUntilMs = 0
        java.util.Arrays.fill(hash, 0.toByte())
        return true
    }

    /**
     * Verifies [pin]. On success resets attempts and returns [Result.Success].
     * On failure increments attempts and may start a throttle. If currently
     * throttled, returns [Result.Throttled] with remaining millis without even
     * checking the PIN. Caller should zero [pin] after.
     */
    fun verifyPin(pin: CharArray): Result {
        val now = clock()
        val lockoutUntil = store.lockoutUntilMs
        if (lockoutUntil > now) {
            return Result.Throttled(remainingMs = lockoutUntil - now)
        }
        val saltStr = store.pinSalt
        val hashStr = store.pinHash
        if (saltStr == null || hashStr == null) return Result.NotSet

        val candidate = hasher.hash(pin, decode(saltStr))
        val stored = decode(hashStr)
        val ok = MessageDigest.isEqual(candidate, stored)
        java.util.Arrays.fill(candidate, 0.toByte())
        java.util.Arrays.fill(stored, 0.toByte())

        return if (ok) {
            store.failedAttempts = 0
            store.lockoutUntilMs = 0
            Result.Success
        } else {
            val attempts = store.failedAttempts + 1
            store.failedAttempts = attempts
            if (attempts >= MAX_ATTEMPTS) {
                // Escalating delay: base * 2^(attempts beyond the threshold).
                val over = attempts - MAX_ATTEMPTS
                val delay = THROTTLE_BASE_MS shl over.coerceAtMost(MAX_THROTTLE_SHIFT)
                store.lockoutUntilMs = now + delay
                Result.Throttled(remainingMs = delay)
            } else {
                Result.Wrong(remainingAttempts = MAX_ATTEMPTS - attempts)
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        // Only meaningful when a PIN exists; the PIN is always the base factor.
        if (isEnabled()) store.biometricEnabled = enabled
    }

    /** Disables App Lock entirely (used by Disable and forgot-PIN reset). */
    fun disable() {
        store.clear()
    }

    sealed interface Result {
        data object Success : Result
        data object NotSet : Result
        data class Wrong(val remainingAttempts: Int) : Result
        data class Throttled(val remainingMs: Long) : Result
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val THROTTLE_BASE_MS = 30_000L // 30s after the 5th wrong attempt
        const val MAX_THROTTLE_SHIFT = 4      // cap escalation at 30s<<4 = 8min
        const val DEFAULT_AUTOLOCK_MS = 30_000L
        private const val SALT_BYTES = 16

        fun encode(bytes: ByteArray): String =
            java.util.Base64.getEncoder().encodeToString(bytes)

        fun decode(s: String): ByteArray =
            java.util.Base64.getDecoder().decode(s)
    }
}
