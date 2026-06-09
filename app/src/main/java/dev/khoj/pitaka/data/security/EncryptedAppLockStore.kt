package dev.khoj.pitaka.data.security

import android.content.Context
import dev.khoj.pitaka.data.crypto.aead.AeadPreferenceStore
import dev.khoj.pitaka.data.crypto.aead.AeadStores
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted-at-rest [AppLockStore] (audit F-03: migrated off the deprecated
 * `androidx.security.crypto` to the app's own [AeadPreferenceStore]). Lives in
 * its OWN pref file and Keystore alias, fully separate from the vault's
 * secrets — App Lock is independent of the vault (PLAN.md decision A).
 *
 * [AeadPreferenceStore] is string-valued; the numeric/boolean fields are
 * serialised to strings and parsed back with safe defaults (a missing or
 * corrupt entry reads as the same default EncryptedSharedPreferences used).
 */
@Singleton
class EncryptedAppLockStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLockStore {

    private val store: AeadPreferenceStore by lazy {
        AeadStores.create(context, PREF_FILE, KEY_ALIAS)
    }

    override var pinSalt: String?
        get() = store.getString(KEY_SALT)
        set(value) { if (value == null) store.remove(KEY_SALT) else store.putString(KEY_SALT, value) }

    override var pinHash: String?
        get() = store.getString(KEY_HASH)
        set(value) { if (value == null) store.remove(KEY_HASH) else store.putString(KEY_HASH, value) }

    override var biometricEnabled: Boolean
        get() = store.getString(KEY_BIOMETRIC)?.toBooleanStrictOrNull() ?: false
        set(value) { store.putString(KEY_BIOMETRIC, value.toString()) }

    override var autoLockTimeoutMs: Long
        get() = store.getString(KEY_TIMEOUT)?.toLongOrNull() ?: 0L
        set(value) { store.putString(KEY_TIMEOUT, value.toString()) }

    override var failedAttempts: Int
        get() = store.getString(KEY_ATTEMPTS)?.toIntOrNull() ?: 0
        set(value) { store.putString(KEY_ATTEMPTS, value.toString()) }

    override var lockoutUntilMs: Long
        get() = store.getString(KEY_LOCKOUT)?.toLongOrNull() ?: 0L
        set(value) { store.putString(KEY_LOCKOUT, value.toString()) }

    override fun clear() {
        store.clear()
    }

    companion object {
        // F-18: own Keystore alias (was "pitaka_applock_master_v1").
        private const val KEY_ALIAS = "pitaka_applock_key_v1"
        private const val PREF_FILE = "pitaka_applock_secrets"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_TIMEOUT = "autolock_timeout_ms"
        private const val KEY_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT = "lockout_until_ms"
    }
}
