package dev.khoj.pitaka.data.publish

import android.content.Context
import dev.khoj.pitaka.data.crypto.aead.AeadPreferenceStore
import dev.khoj.pitaka.data.crypto.aead.AeadStores
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-01 (audit): cover filenames in the public repo must not leak the
 * internal book id.
 *
 * On first use, generates a 16-byte salt and persists it encrypted at rest
 * (AeadPreferenceStore). The published cover for book id `n` is then
 * `covers/${shortHex(sha256(salt || ascii(n)))}.jpg`. The hash is stable
 * across publishes (same id → same path) so external links to a cover
 * keep working between updates of the same library.
 *
 * Hash length: 16 hex chars (64 bits). Across a 100k-book library the
 * birthday-collision probability is roughly 2.7e-10, well below any rate
 * we'd ever notice in practice.
 *
 * Storage layout: separate prefs file from [GitHubCredentialStore] so a
 * future "sign out of GitHub" wipe doesn't churn the cover salt and
 * accidentally invalidate every existing cover URL on the public page.
 */
class PublishCoverIds(
    private val saltStore: PublishCoverSaltStore,
) {
    @Inject
    constructor(impl: EncryptedPublishCoverSaltStore) : this(impl as PublishCoverSaltStore)

    /**
     * Returns the relative publish path for a cover of [bookId], e.g.
     * `covers/3f2c7a1b9e4d8051.jpg`. Deterministic for a given (salt,
     * bookId) pair.
     */
    fun pathFor(bookId: Long): String {
        val salt = saltStore.salt()
        return "covers/${shortHashHex(salt, bookId)}.jpg"
    }

    companion object {
        /** Bytes of hex (64 bits) — see class doc for collision analysis. */
        const val HASH_HEX_CHARS: Int = 16

        /**
         * Pure helper, exposed for tests. SHA-256 of `salt || ascii(id)`,
         * hex, first [HASH_HEX_CHARS] characters.
         */
        fun shortHashHex(salt: ByteArray, bookId: Long): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(bookId.toString().toByteArray(Charsets.US_ASCII))
            val digest = md.digest()
            val sb = StringBuilder(HASH_HEX_CHARS)
            // Emit only as many bytes as we need to fill HASH_HEX_CHARS hex
            // characters, two hex chars per byte.
            val byteCount = (HASH_HEX_CHARS + 1) / 2
            for (i in 0 until byteCount) {
                val v = digest[i].toInt() and 0xFF
                sb.append(Character.forDigit((v ushr 4) and 0x0F, 16))
                sb.append(Character.forDigit(v and 0x0F, 16))
            }
            return sb.substring(0, HASH_HEX_CHARS)
        }
    }
}

/**
 * Storage seam for the publish-cover salt. Production binding is
 * [EncryptedPublishCoverSaltStore]; tests inject a fake with a known salt.
 *
 * The salt is **not** a high-value secret in the way the GitHub token is —
 * an attacker with disk read access already has the unencrypted
 * `books.db`. The salt's purpose is to prevent the *public web page* from
 * leaking enumerable internal ids. Encrypted-at-rest is appropriate
 * defence-in-depth but not load-bearing.
 */
interface PublishCoverSaltStore {
    /** Returns the persisted salt, generating it on first call. */
    fun salt(): ByteArray
}

/** [AeadPreferenceStore]-backed [PublishCoverSaltStore] (audit F-03). */
@Singleton
class EncryptedPublishCoverSaltStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PublishCoverSaltStore {

    private val store: AeadPreferenceStore by lazy {
        AeadStores.create(context, PREF_FILE, KEY_ALIAS)
    }

    override fun salt(): ByteArray {
        store.getBytes(KEY_SALT)?.let { return it }
        val fresh = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        store.putBytes(KEY_SALT, fresh)
        return fresh
    }

    companion object {
        // F-18: own Keystore alias (was the shared "pitaka_publish_master_v1").
        // Pref file stays separate from GitHubCredentialStore so a token wipe
        // and cover-path stability remain decoupled.
        private const val KEY_ALIAS = "pitaka_cover_salt_key_v1"
        private const val PREF_FILE = "pitaka_publish_cover_salt"
        private const val KEY_SALT = "cover_salt_v1"
        private const val SALT_BYTES = 16
    }
}
