package dev.khoj.pitaka.data.crypto

/**
 * Holder for the unwrapped 32-byte vault passphrase.
 *
 * Backed by a `ByteArray` we zero out on [close]. Once closed the bytes are
 * irreversibly destroyed and any further `bytes` access throws.
 *
 * Pass this only through trusted code (the vault session, the SQLCipher
 * factory). Never log it, never serialize it.
 */
class VaultPassphrase private constructor(
    private val data: ByteArray,
) : AutoCloseable {

    private var closed: Boolean = false

    val bytes: ByteArray
        get() {
            check(!closed) { "VaultPassphrase already closed" }
            return data
        }

    /** Returns a defensive copy of the bytes; callers must zero it themselves. */
    fun copyBytes(): ByteArray = bytes.copyOf()

    override fun close() {
        if (closed) return
        java.util.Arrays.fill(data, 0.toByte())
        closed = true
    }

    companion object {
        const val PASSPHRASE_BYTES = 32

        /** Wraps the given bytes verbatim. Use [generate] for new passphrases. */
        fun of(bytes: ByteArray): VaultPassphrase {
            require(bytes.size == PASSPHRASE_BYTES) {
                "Vault passphrase must be exactly $PASSPHRASE_BYTES bytes; got ${bytes.size}."
            }
            return VaultPassphrase(bytes.copyOf())
        }

        /** Generates a fresh cryptographically-random passphrase. */
        fun generate(): VaultPassphrase {
            val buf = ByteArray(PASSPHRASE_BYTES)
            java.security.SecureRandom().nextBytes(buf)
            return VaultPassphrase(buf)
        }
    }
}
