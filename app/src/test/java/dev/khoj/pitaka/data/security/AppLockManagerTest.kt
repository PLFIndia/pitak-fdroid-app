package dev.khoj.pitaka.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure-JVM tests for AppLockManager: hash/verify, no-plaintext storage,
 * escalating lockout throttle, biometric flag, disable. Uses in-memory fakes for
 * the store and a fast deterministic hasher (no Argon2 / Android).
 */
class AppLockManagerTest {

    private class FakeStore : AppLockStore {
        override var pinSalt: String? = null
        override var pinHash: String? = null
        override var biometricEnabled: Boolean = false
        override var autoLockTimeoutMs: Long = 0L
        override var failedAttempts: Int = 0
        override var lockoutUntilMs: Long = 0L
        override fun clear() {
            pinSalt = null; pinHash = null; biometricEnabled = false
            autoLockTimeoutMs = 0L; failedAttempts = 0; lockoutUntilMs = 0L
        }
    }

    // Deterministic SHA-256(salt || pin) stand-in for Argon2 — fast, salted.
    private val fakeHasher = object : PinHasher {
        override fun hash(pin: CharArray, salt: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(String(pin).toByteArray(Charsets.UTF_8))
            return md.digest()
        }
    }

    private var now = 1_000_000L
    private fun manager(store: AppLockStore = FakeStore()) =
        AppLockManager(store, fakeHasher, clock = { now }, random = SecureRandom())

    @Test
    fun not_enabled_until_pin_set() {
        val m = manager()
        assertThat(m.isEnabled()).isFalse()
        assertThat(m.setPin("1234".toCharArray())).isTrue()
        assertThat(m.isEnabled()).isTrue()
    }

    @Test
    fun rejects_too_short_pin() {
        val m = manager()
        assertThat(m.setPin("12".toCharArray())).isFalse()
        assertThat(m.isEnabled()).isFalse()
    }

    @Test
    fun pin_is_never_stored_in_plaintext() {
        val store = FakeStore()
        manager(store).setPin("4729".toCharArray())
        assertThat(store.pinHash).isNotNull()
        assertThat(store.pinSalt).isNotNull()
        // The raw PIN must not appear in either stored field.
        assertThat(store.pinHash).doesNotContain("4729")
        assertThat(store.pinSalt).doesNotContain("4729")
    }

    @Test
    fun correct_pin_verifies_wrong_pin_does_not() {
        val m = manager()
        m.setPin("4729".toCharArray())
        assertThat(m.verifyPin("4729".toCharArray())).isInstanceOf(AppLockManager.Result.Success::class.java)
        assertThat(m.verifyPin("0000".toCharArray())).isInstanceOf(AppLockManager.Result.Wrong::class.java)
    }

    @Test
    fun escalating_throttle_after_max_attempts() {
        val store = FakeStore()
        val m = manager(store)
        m.setPin("4729".toCharArray())
        // First MAX_ATTEMPTS-1 wrong attempts return Wrong with decreasing budget.
        repeat(AppLockManager.MAX_ATTEMPTS - 1) {
            assertThat(m.verifyPin("0000".toCharArray())).isInstanceOf(AppLockManager.Result.Wrong::class.java)
        }
        // The MAX_ATTEMPTS-th wrong attempt throttles.
        val throttled = m.verifyPin("0000".toCharArray())
        assertThat(throttled).isInstanceOf(AppLockManager.Result.Throttled::class.java)
        assertThat((throttled as AppLockManager.Result.Throttled).remainingMs)
            .isEqualTo(AppLockManager.THROTTLE_BASE_MS)

        // While throttled, even the CORRECT pin is rejected as Throttled.
        assertThat(m.verifyPin("4729".toCharArray())).isInstanceOf(AppLockManager.Result.Throttled::class.java)

        // After the throttle window passes, the correct pin works again.
        now += AppLockManager.THROTTLE_BASE_MS + 1
        assertThat(m.verifyPin("4729".toCharArray())).isInstanceOf(AppLockManager.Result.Success::class.java)
    }

    @Test
    fun successful_verify_resets_attempts() {
        val store = FakeStore()
        val m = manager(store)
        m.setPin("4729".toCharArray())
        m.verifyPin("0000".toCharArray())
        m.verifyPin("0000".toCharArray())
        assertThat(store.failedAttempts).isEqualTo(2)
        m.verifyPin("4729".toCharArray())
        assertThat(store.failedAttempts).isEqualTo(0)
        assertThat(store.lockoutUntilMs).isEqualTo(0L)
    }

    @Test
    fun biometric_flag_requires_enabled() {
        val m = manager()
        m.setBiometricEnabled(true)
        assertThat(m.isBiometricEnabled()).isFalse() // no PIN yet
        m.setPin("4729".toCharArray())
        m.setBiometricEnabled(true)
        assertThat(m.isBiometricEnabled()).isTrue()
    }

    @Test
    fun disable_clears_everything() {
        val store = FakeStore()
        val m = manager(store)
        m.setPin("4729".toCharArray())
        m.setBiometricEnabled(true)
        m.disable()
        assertThat(m.isEnabled()).isFalse()
        assertThat(store.pinHash).isNull()
        assertThat(store.biometricEnabled).isFalse()
    }

    @Test
    fun verify_with_no_pin_returns_not_set() {
        val m = manager()
        assertThat(m.verifyPin("4729".toCharArray())).isInstanceOf(AppLockManager.Result.NotSet::class.java)
    }
}
