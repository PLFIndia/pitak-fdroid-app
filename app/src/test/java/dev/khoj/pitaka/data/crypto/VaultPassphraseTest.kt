package dev.khoj.pitaka.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VaultPassphraseTest {

    @Test
    fun generate_produces_32_byte_random_passphrase() {
        val p1 = VaultPassphrase.generate()
        val p2 = VaultPassphrase.generate()
        try {
            assertThat(p1.bytes).hasLength(32)
            assertThat(p2.bytes).hasLength(32)
            assertThat(p1.bytes.contentEquals(p2.bytes)).isFalse()
        } finally {
            p1.close()
            p2.close()
        }
    }

    @Test
    fun close_zeroes_the_buffer_and_blocks_further_access() {
        val p = VaultPassphrase.generate()
        p.close()
        try {
            p.bytes
            error("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun of_rejects_wrong_length() {
        try {
            VaultPassphrase.of(ByteArray(31))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun copyBytes_returns_independent_array() {
        val p = VaultPassphrase.generate()
        try {
            val a = p.copyBytes()
            val b = p.copyBytes()
            assertThat(a).isEqualTo(b)
            a[0] = (a[0] + 1).toByte()
            assertThat(a).isNotEqualTo(b)
        } finally {
            p.close()
        }
    }
}
