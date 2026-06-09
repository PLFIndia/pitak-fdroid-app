package dev.khoj.pitaka.data.crypto.aead

/**
 * Authenticated Encryption with Associated Data.
 *
 * Design borrowed from Google Tink's `Aead` interface (the *shape* only —
 * not the dependency, per PLAN.md Q4 = option A). A single primitive that
 * encrypts a plaintext into a self-describing ciphertext (IV prepended,
 * GCM auth tag appended) and decrypts it back, failing loudly on any
 * tamper. Callers never see the IV or the key.
 *
 * On-disk format produced by [AesGcmAead] is byte-identical to the layout
 * [dev.khoj.pitaka.data.crypto.KeystoreVault] has shipped since v1:
 *
 *     Base64-NO_WRAP( 12-byte IV || ciphertext+16-byte-GCM-tag )
 *
 * so the whole app has exactly one at-rest secret format (AGENTS §12,
 * single source of truth).
 */
interface Aead {

    /**
     * Encrypts [plaintext], binding [associatedData] (default empty) into
     * the GCM tag. The returned bytes carry their own IV and tag; pass them
     * verbatim to [decrypt].
     */
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = EMPTY_AAD): ByteArray

    /**
     * Decrypts a blob produced by [encrypt]. Throws
     * [javax.crypto.AEADBadTagException] (or a subclass of
     * [java.security.GeneralSecurityException]) if the ciphertext or
     * [associatedData] was modified, or the wrong key is used. Never
     * returns corrupt plaintext.
     */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray = EMPTY_AAD): ByteArray

    companion object {
        val EMPTY_AAD: ByteArray = ByteArray(0)
    }
}
