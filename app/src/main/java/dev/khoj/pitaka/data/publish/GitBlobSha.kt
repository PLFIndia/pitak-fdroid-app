package dev.khoj.pitaka.data.publish

import java.security.MessageDigest

/**
 * Computes the **git blob SHA-1** of a byte array, locally, without uploading.
 *
 * Git names a blob object by `sha1("blob " + <byteLength> + "\u0000" + <bytes>)`.
 * This is exactly the `sha` GitHub's Git Data / Trees API reports for a file's
 * content, so a locally-computed value is directly comparable to the sha stored
 * in our publish manifest (and to what `getTreeRecursive` returns). That lets a
 * repeat publish decide "this file is unchanged, skip it" with zero network.
 *
 * Verified in GitBlobShaTest against `git hash-object` reference values.
 *
 * Note: this is content addressing, not a security hash — SHA-1's collision
 * weakness is irrelevant here (we're matching git's own object names, and an
 * attacker with write access to the user's repo already owns the page).
 */
object GitBlobSha {

    /** Lowercase 40-char hex git blob sha of [bytes]. */
    fun of(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-1")
        md.update(header)
        md.update(bytes)
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit((v ushr 4) and 0x0F, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }
}
