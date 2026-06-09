package dev.khoj.pitaka.data.crypto.aead

import android.content.Context

/**
 * Builds an [AeadPreferenceStore] for a named preferences file, sealed with a
 * per-file Android Keystore key. The single construction point for the
 * raw-secret stores migrated off `androidx.security.crypto` (audit F-03), so
 * the per-store alias / pref-file pairing (audit F-18) is established in one
 * place instead of copy-pasted into each store.
 *
 * @param prefFile  the SharedPreferences file name (one per store).
 * @param keyAlias  the Android Keystore alias (one per store — F-18).
 * @param authBound F-06; only the vault key sets this.
 * @param preferStrongBox F-19; request the secure element with TEE fallback.
 */
object AeadStores {

    fun create(
        context: Context,
        prefFile: String,
        keyAlias: String,
        authBound: Boolean = false,
        preferStrongBox: Boolean = false,
    ): AeadPreferenceStore {
        val prefs = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)
        val key = KeystoreKeyProvider(
            alias = keyAlias,
            authBound = authBound,
            preferStrongBox = preferStrongBox,
        ).loadOrCreateKey()
        return AeadPreferenceStore(prefs, AesGcmAead(key))
    }
}
