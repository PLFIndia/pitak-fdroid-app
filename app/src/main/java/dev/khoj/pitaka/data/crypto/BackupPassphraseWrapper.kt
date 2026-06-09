package dev.khoj.pitaka.data.crypto

import android.content.Context
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D5 backup-passphrase wrapper.
 *
 * The user provides a passphrase string. We derive a 32-byte key from it
 * via Argon2id, then AES-GCM-encrypt the 32-byte vault passphrase under
 * that derived key. The wrapped blob (along with the Argon2 salt and an
 * optional hint per D19) is stored on disk and is additionally included in
 * Phase-6 backup archives.
 *
 * At-rest encryption note (audit F-03 / option A): this blob is ALREADY
 * ciphertext — AES-GCM under an Argon2id-derived key whose input (the user's
 * passphrase) never touches disk. The identical bytes already travel
 * off-device inside backup archives. Wrapping them a second time under a
 * Keystore key (as the old `EncryptedSharedPreferences` did) bought no
 * confidentiality, so the migration off the deprecated library stores the
 * blob in an ordinary [android.content.SharedPreferences]. Single
 * encryption, at the layer that actually protects the secret.
 *
 * Forgetting the daily biometric does nothing destructive — it's only a
 * UI gate. Forgetting the backup passphrase means prior backup files are
 * unreadable; the on-device vault is unaffected because the passphrase
 * here is independent from the Keystore-wrapped daily-use blob.
 *
 * Argon2id parameters: t=3, m=64MB, p=1. Reasonable for daily unlock and
 * resistant to commodity-GPU brute-forcing of the backup file.
 */
@Singleton
class BackupPassphraseWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    data class WrappedBlob(
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun isSet(): Boolean = prefs.getString(KEY_BLOB, null) != null

    /** Optional hint, displayed on the forgot-passphrase screen (D19). */
    fun getHint(): String? = prefs.getString(KEY_HINT, null)?.takeIf { it.isNotBlank() }

    /**
     * Wraps [vaultPassphrase] (the 32-byte daily-use blob) under a key
     * derived from [passphrase]. Persists salt + IV + ciphertext + the
     * optional [hint].
     */
    fun setBackupPassphrase(
        vaultPassphrase: VaultPassphrase,
        passphrase: CharArray,
        hint: String?,
    ) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(passphrase, salt)
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(vaultPassphrase.bytes)
            persist(WrappedBlob(salt, iv, ciphertext), hint)
        } finally {
            java.util.Arrays.fill(derived, 0.toByte())
        }
    }

    /**
     * Attempts to unwrap the stored vault passphrase using [passphrase].
     * Returns the unwrapped [VaultPassphrase] on success, or null on
     * wrong passphrase / corrupt blob / no backup set.
     */
    fun unwrapWithBackupPassphrase(passphrase: CharArray): VaultPassphrase? {
        val blob = load() ?: return null
        val derived = deriveKey(passphrase, blob.salt)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_BITS, blob.iv))
            val plaintext = cipher.doFinal(blob.ciphertext)
            VaultPassphrase.of(plaintext).also {
                java.util.Arrays.fill(plaintext, 0.toByte())
            }
        } catch (t: Throwable) {
            null
        } finally {
            java.util.Arrays.fill(derived, 0.toByte())
        }
    }

    /** Used by D19 wipe path. */
    fun clear() {
        prefs.edit()
            .remove(KEY_BLOB)
            .remove(KEY_HINT)
            .apply()
    }

    /**
     * Phase 6 backup export: returns the wrapped-blob payload as bytes
     * for inclusion in the backup archive.
     *
     * Contract (F-07 / audit): the returned bytes are the UTF-8 / ASCII
     * encoding of `<base64-salt>.<base64-iv>.<base64-ciphertext>`. The
     * blob never contains arbitrary binary — each segment is base64 —
     * so a string round-trip is well-defined.
     *
     * Returns null when no backup passphrase has been set (the backup
     * archive then carries `hasBackupBlob=false` and restore on a new
     * device requires re-setting the passphrase).
     */
    fun rawBlobBytes(): ByteArray? {
        val raw = prefs.getString(KEY_BLOB, null) ?: return null
        return raw.toByteArray(Charsets.UTF_8)
    }

    /**
     * Outcome of [setRawBlobBytes]. F-07: distinguishes a corrupt archive
     * (caller should surface "backup is broken") from a wrong passphrase
     * (caller should let the user retry).
     */
    sealed interface SetBlobResult {
        data object Success : SetBlobResult
        data class InvalidFormat(val reason: String) : SetBlobResult
    }

    /**
     * Phase 6 restore: writes a previously-exported raw blob back into
     * EncryptedSharedPreferences, restoring the user's backup passphrase
     * on the new device.
     *
     * F-07 (audit): [bytes] is validated against [BlobFormat] BEFORE any
     * prefs are written. Invalid input returns [SetBlobResult.InvalidFormat]
     * with a short diagnostic; nothing is persisted. Valid input is stored
     * as-is and [SetBlobResult.Success] is returned.
     */
    fun setRawBlobBytes(bytes: ByteArray, hint: String?): SetBlobResult {
        val asString = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            ?: return SetBlobResult.InvalidFormat("blob is not valid UTF-8")
        BlobFormat.parse(asString)?.let { /* parses cleanly */ }
            ?: return SetBlobResult.InvalidFormat("blob does not match <b64>.<b64>.<b64> shape")
        prefs.edit()
            .putString(KEY_BLOB, asString)
            .apply {
                if (hint != null) putString(KEY_HINT, hint) else remove(KEY_HINT)
            }
            .apply()
        return SetBlobResult.Success
    }

    /**
     * Pure validator for the on-disk blob string. Exposed for unit tests.
     * The shape is fixed by [persist]: three base64-NO_WRAP segments
     * separated by `.`, the segments being SALT_BYTES, GCM_IV_BYTES, and
     * ciphertext (variable length). Each segment must base64-decode
     * cleanly; the salt and IV must have the expected sizes.
     */
    object BlobFormat {
        fun parse(s: String): WrappedBlob? {
            val parts = s.split(".")
            if (parts.size != 3) return null
            val salt = runCatching {
                android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            }.getOrNull() ?: return null
            val iv = runCatching {
                android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            }.getOrNull() ?: return null
            val ciphertext = runCatching {
                android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP)
            }.getOrNull() ?: return null
            if (salt.size != SALT_BYTES) return null
            if (iv.size != GCM_IV_BYTES) return null
            if (ciphertext.isEmpty()) return null
            return WrappedBlob(salt, iv, ciphertext)
        }

        fun isValid(bytes: ByteArray): Boolean {
            val s = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return false
            return parse(s) != null
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val pwBytes = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            val result: Argon2KtResult = Argon2Kt().hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pwBytes,
                salt = salt,
                tCostInIterations = ARGON2_T_COST,
                mCostInKibibyte = ARGON2_M_COST_KIB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = KEY_BYTES,
            )
            return result.rawHashAsByteArray()
        } finally {
            java.util.Arrays.fill(pwBytes, 0.toByte())
        }
    }

    private fun persist(blob: WrappedBlob, hint: String?) {
        prefs.edit()
            .putString(
                KEY_BLOB,
                encode(blob.salt) + "." + encode(blob.iv) + "." + encode(blob.ciphertext),
            )
            .apply {
                if (hint != null) putString(KEY_HINT, hint) else remove(KEY_HINT)
            }
            .apply()
    }

    private fun load(): WrappedBlob? {
        val raw = prefs.getString(KEY_BLOB, null) ?: return null
        return BlobFormat.parse(raw)
    }

    private fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    companion object {
        // Shares the "pitaka_vault_secrets" file with KeystoreVault (different
        // keys); both are now plain SharedPreferences. The blob stored here is
        // already Argon2-AES-GCM ciphertext (see class doc) — no Keystore alias.
        private const val PREF_FILE = "pitaka_vault_secrets"
        private const val KEY_BLOB = "backup_blob"
        private const val KEY_HINT = "backup_hint"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val SALT_BYTES = 16
        private const val KEY_BYTES = 32

        // Argon2id parameters tuned for current-gen Android phones.
        const val ARGON2_T_COST = 3
        const val ARGON2_M_COST_KIB = 65_536 // 64MB
        const val ARGON2_PARALLELISM = 1
    }
}
