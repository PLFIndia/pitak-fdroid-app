package dev.khoj.pitaka.data.crypto.aead

import android.content.SharedPreferences
import android.util.Base64

/**
 * A small encrypted key/value store: AES-GCM via [Aead] over an ordinary
 * [SharedPreferences] file. Drop-in replacement for the deprecated
 * `androidx.security.crypto.EncryptedSharedPreferences` (audit F-03), whose
 * library went to no-active-development in late 2024.
 *
 * What changed vs. EncryptedSharedPreferences:
 *  - Values are sealed with the app's own [AesGcmAead] (one audited format),
 *    not the AndroidX SIV/GCM scheme.
 *  - Each value is bound to its own KEY NAME as GCM associated data, so a
 *    ciphertext copied from one key to another fails to decrypt (defends
 *    against on-disk value-shuffling).
 *  - Keys (the pref names) are stored in clear. The threat model here is
 *    disk-read of values at rest; the key *names* are fixed compile-time
 *    constants ("gh_token", "pin_hash", …) and reveal nothing the schema
 *    doesn't. EncryptedSharedPreferences encrypted key names via AES-SIV; we
 *    deliberately drop that — it bought no real confidentiality for static
 *    names and added a second algorithm. Documented trade-off (AGENTS §12).
 *
 * Persisted value form: `Base64-NO_WRAP(IV || ciphertext || tag)` — a String,
 * honestly a String (contrast the F-07 bug where binary was crammed through
 * UTF-8). Reads of malformed/truncated values return null rather than throwing,
 * so a corrupt entry degrades to "absent", not a crash.
 */
class AeadPreferenceStore(
    private val prefs: SharedPreferences,
    private val aead: Aead,
) {

    fun getString(key: String): String? = getBytes(key)?.toString(Charsets.UTF_8)

    fun putString(key: String, value: String) = putBytes(key, value.toByteArray(Charsets.UTF_8))

    fun getBytes(key: String): ByteArray? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            aead.decrypt(blob, aad(key))
        }.getOrNull()
    }

    fun putBytes(key: String, value: ByteArray) {
        val blob = aead.encrypt(value, aad(key))
        prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clear() = prefs.edit().clear().apply()

    /** GCM associated data = the key name, binding ciphertext to its slot. */
    private fun aad(key: String): ByteArray = key.toByteArray(Charsets.UTF_8)
}
