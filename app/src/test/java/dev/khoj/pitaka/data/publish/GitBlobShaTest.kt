package dev.khoj.pitaka.data.publish

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies [GitBlobSha] against reference values produced by `git hash-object`
 * itself, so our local "has this file changed?" diff is guaranteed to match the
 * sha GitHub's Git Data / Trees API reports. If this ever drifts, the whole
 * incremental-publish skip logic would silently mis-decide — hence the
 * git-authoritative fixtures.
 *
 * Reference values (captured from git):
 *   printf ''        | git hash-object --stdin -> e69de29b...
 *   printf 'hello'   | git hash-object --stdin -> b6fc4c62...
 *   printf 'hello\n' | git hash-object --stdin -> ce013625...
 *   printf '{"a":1}' | git hash-object --stdin -> daa5053e...
 */
class GitBlobShaTest {

    @Test
    fun empty_blob_matches_git() {
        assertThat(GitBlobSha.of(ByteArray(0)))
            .isEqualTo("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391")
    }

    @Test
    fun ascii_no_newline_matches_git() {
        assertThat(GitBlobSha.of("hello".toByteArray(Charsets.US_ASCII)))
            .isEqualTo("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0")
    }

    @Test
    fun ascii_trailing_newline_matches_git() {
        // Proves the byte-length header counts the newline (6 bytes, not 5).
        assertThat(GitBlobSha.of("hello\n".toByteArray(Charsets.US_ASCII)))
            .isEqualTo("ce013625030ba8dba906f756967f9e9ca394464a")
    }

    @Test
    fun json_blob_matches_git() {
        assertThat(GitBlobSha.of("""{"a":1}""".toByteArray(Charsets.UTF_8)))
            .isEqualTo("daa5053ecf5f9a37b2de733d0751cc1ab53ac010")
    }

    @Test
    fun sha_is_40_lowercase_hex_for_binary_bytes() {
        // Binary (JPEG-like) content: we don't need a git oracle for the exact
        // value here — the ASCII cases already prove the header math — but the
        // output shape must be a 40-char lowercase hex string.
        val bytes = ByteArray(5_000) { (it % 256).toByte() }
        val sha = GitBlobSha.of(bytes)
        assertThat(sha).hasLength(40)
        assertThat(sha).matches("[0-9a-f]{40}")
    }

    @Test
    fun same_bytes_same_sha_different_bytes_different_sha() {
        val a = GitBlobSha.of("cover-A".toByteArray())
        val a2 = GitBlobSha.of("cover-A".toByteArray())
        val b = GitBlobSha.of("cover-B".toByteArray())
        assertThat(a).isEqualTo(a2)
        assertThat(a).isNotEqualTo(b)
    }
}
