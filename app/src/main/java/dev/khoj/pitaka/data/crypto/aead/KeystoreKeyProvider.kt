package dev.khoj.pitaka.data.crypto.aead

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Provisions a non-exportable AES-256-GCM key in the Android Keystore and
 * hands it to [AesGcmAead]. This is the *only* part of the AEAD spine that
 * touches the Keystore, so it is the on-device boundary (the project has no
 * Robolectric `AndroidKeyStore`; verified by the Wave-6.2 device gate, not a
 * unit test — consistent with KeystoreVault, which likewise has no unit test).
 *
 * Per-store isolation (audit F-18): every store passes its OWN [alias], so no
 * two preference files share a Keystore key. Deleting one store's key
 * (cleanup, sign-out, wipe) can no longer brick an unrelated store.
 *
 * Auth-binding (audit F-06): pass [authBound] = true to set
 * `setUserAuthenticationRequired(true)`. Only the vault key uses this; the
 * app-level biometric gate stays, but the key itself now refuses to decrypt
 * without a recent device-credential / biometric auth, so any code path that
 * reaches the vault unwrap without authentication fails closed.
 *
 * StrongBox (audit F-19): pass [preferStrongBox] = true to request the
 * hardware secure element when present, with an automatic fall back to the
 * TEE-backed key on devices without StrongBox — a naive
 * `setIsStrongBoxBacked(true)` throws [StrongBoxUnavailableException] on those
 * devices, so the fallback is mandatory, not optional.
 *
 * @param keyValiditySeconds when [authBound], how long after an authentication
 *        the key stays usable. -1 means "every use needs auth" (strongest);
 *        a positive value allows a grace window. Ignored when not auth-bound.
 */
class KeystoreKeyProvider(
    private val alias: String,
    private val authBound: Boolean = false,
    private val preferStrongBox: Boolean = false,
    private val keyValiditySeconds: Int = -1,
) {

    /** Loads the existing key for [alias], or creates it on first call. */
    fun loadOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return generate(preferStrongBox)
    }

    /** Deletes this store's key. Idempotent; safe if the alias is absent. */
    fun deleteKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = baseSpec(strongBox).build()
        return try {
            generator.init(spec)
            generator.generateKey()
        } catch (e: java.security.ProviderException) {
            // F-19: StrongBoxUnavailableException (API 28+) is a ProviderException
            // subclass. Catching the parent avoids referencing the API-28 type on
            // API 26/27 devices. If we weren't asking for StrongBox, it's a real
            // failure — rethrow. If we were, fall back to the TEE-backed key.
            if (!strongBox) throw e
            generate(strongBox = false)
        }
    }

    private fun baseSpec(strongBox: Boolean): KeyGenParameterSpec.Builder {
        val b = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (authBound) {
            // F-06: the key itself is gated on a recent user authentication.
            b.setUserAuthenticationRequired(true)
            @Suppress("DEPRECATION")
            b.setUserAuthenticationValidityDurationSeconds(keyValiditySeconds)
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // F-19: request the secure element; generate() falls back if absent.
            b.setIsStrongBoxBacked(true)
        }
        return b
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
