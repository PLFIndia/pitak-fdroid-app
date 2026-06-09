package dev.khoj.pitaka.data.security

/**
 * PIN hashing seam. Production uses Argon2id (same KDF as the backup
 * passphrase); tests use a fast deterministic fake so unit tests don't run a
 * 64 MB memory-hard hash.
 */
interface PinHasher {
    /** Derives a hash of [pin] with the given [salt]. Deterministic for a (pin, salt) pair. */
    fun hash(pin: CharArray, salt: ByteArray): ByteArray
}
