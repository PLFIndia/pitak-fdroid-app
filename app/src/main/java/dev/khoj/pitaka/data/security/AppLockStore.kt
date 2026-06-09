package dev.khoj.pitaka.data.security

/**
 * Storage seam for App Lock secrets/state. The production implementation is
 * backed by EncryptedSharedPreferences; tests use an in-memory fake. Keeping
 * this an interface lets [AppLockManager]'s logic (hash/verify/throttle) be
 * unit-tested on the JVM without Android.
 *
 * All values are App-Lock-specific and live in their OWN encrypted pref file,
 * separate from the vault's secrets (App Lock is independent of the vault).
 */
interface AppLockStore {
    /** Base64(salt) of the stored PIN hash, or null when no PIN is set. */
    var pinSalt: String?

    /** Base64(Argon2id hash) of the stored PIN, or null when no PIN is set. */
    var pinHash: String?

    /** Whether the user opted into biometric unlock (PIN is still the base). */
    var biometricEnabled: Boolean

    /** Re-lock timeout in millis after the app is backgrounded. */
    var autoLockTimeoutMs: Long

    /** Consecutive failed PIN attempts (for the lockout throttle). */
    var failedAttempts: Int

    /** Epoch millis until which entry is throttled (0 = not throttled). */
    var lockoutUntilMs: Long

    /** Wipes all App Lock state (disable / forgot-PIN reset). */
    fun clear()
}
