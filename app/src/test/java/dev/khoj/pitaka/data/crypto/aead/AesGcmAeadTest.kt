package dev.khoj.pitaka.data.crypto.aead

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Wave 6.1 (audit F-03 spine): [AesGcmAead] round-trip + tamper-evidence.
 *
 * Uses a plain in-memory JCE AES key, NOT an Android Keystore key — the
 * project has no Robolectric `AndroidKeyStore`, and the algorithm under test
 * is provider-agnostic. The Keystore key-provisioning boundary
 * ([KeystoreKeyProvider]) is verified on-device in the Wave 6.2 gate,
 * matching how KeystoreVault is verified.
 *
 * Robolectric is used (not a plain JUnit runner) only because
 * [AeadPreferenceStore] and the format use `android.util.Base64`; keeping all
 * AEAD tests on one runner is simpler and matches BackupBlobFormatTest.
 */
@RunWith(RobolectricTestRunner::class)
class AesGcmAeadTest {

    private fun freshKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun aead(key: SecretKey = freshKey()) = AesGcmAead(key)

    @Test
    fun round_trips_plaintext() {
        val a = aead()
        val msg = "ghp_exampletoken_देवनागरी_😀".toByteArray(Charsets.UTF_8)
        val ct = a.encrypt(msg)
        assertThat(a.decrypt(ct).toString(Charsets.UTF_8))
            .isEqualTo(msg.toString(Charsets.UTF_8))
    }

    @Test
    fun round_trips_empty_plaintext() {
        val a = aead()
        val ct = a.encrypt(ByteArray(0))
        assertThat(a.decrypt(ct)).isEqualTo(ByteArray(0))
    }

    @Test
    fun ciphertext_carries_iv_prefix_of_expected_size() {
        val a = aead()
        val ct = a.encrypt("x".toByteArray())
        // 12-byte IV + at least the 16-byte GCM tag.
        assertThat(ct.size).isAtLeast(AesGcmAead.IV_BYTES + 16)
    }

    @Test
    fun two_encryptions_of_same_plaintext_differ() {
        // Random IV per call => no deterministic ciphertext (GCM nonce reuse
        // is the one thing that breaks AES-GCM).
        val a = aead()
        val msg = "same".toByteArray()
        assertThat(a.encrypt(msg)).isNotEqualTo(a.encrypt(msg))
    }

    @Test(expected = AEADBadTagException::class)
    fun flipped_ciphertext_bit_fails_to_decrypt() {
        val a = aead()
        val ct = a.encrypt("secret".toByteArray())
        ct[ct.size - 1] = (ct[ct.size - 1].toInt() xor 0x01).toByte()
        a.decrypt(ct)
    }

    @Test(expected = Exception::class)
    fun wrong_key_fails_to_decrypt() {
        val ct = aead().encrypt("secret".toByteArray())
        aead(freshKey()).decrypt(ct) // different key
    }

    @Test
    fun associated_data_is_bound_into_the_tag() {
        val a = aead()
        val ct = a.encrypt("v".toByteArray(), associatedData = "slotA".toByteArray())
        assertThat(a.decrypt(ct, associatedData = "slotA".toByteArray()).toString(Charsets.UTF_8))
            .isEqualTo("v")
        // Same ciphertext, different AAD => rejected.
        var threw = false
        try {
            a.decrypt(ct, associatedData = "slotB".toByteArray())
        } catch (e: Exception) {
            threw = true
        }
        assertThat(threw).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun too_short_ciphertext_is_rejected() {
        aead().decrypt(ByteArray(AesGcmAead.IV_BYTES)) // no room for ciphertext/tag
    }
}
