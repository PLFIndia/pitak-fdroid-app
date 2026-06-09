package dev.khoj.pitaka.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-07 (audit): unit tests for [BackupPassphraseWrapper.BlobFormat].
 *
 * Robolectric is used because the validator calls `android.util.Base64`
 * (the same encoder the production [BackupPassphraseWrapper.persist]\u202F+\u202F
 * load path uses \u2014 single source of truth, per AGENTS \u00a712).
 *
 * Contract under test:
 *   - Valid blob: three base64-NO_WRAP segments separated by `.`,
 *     SALT_BYTES (16), GCM_IV_BYTES (12), ciphertext non-empty.
 *   - Anything else returns null / isValid==false.
 */
@RunWith(RobolectricTestRunner::class)
class BackupBlobFormatTest {

    private fun b64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun goodBlobString(): String =
        b64(ByteArray(16) { 1 }) + "." +
            b64(ByteArray(12) { 2 }) + "." +
            b64(ByteArray(48) { 3 })

    // --- happy path -------------------------------------------------------

    @Test
    fun valid_blob_parses() {
        val parsed = BackupPassphraseWrapper.BlobFormat.parse(goodBlobString())
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.salt).hasLength(16)
        assertThat(parsed.iv).hasLength(12)
        assertThat(parsed.ciphertext).hasLength(48)
    }

    @Test
    fun isValid_accepts_utf8_bytes_of_a_good_blob() {
        val bytes = goodBlobString().toByteArray(Charsets.UTF_8)
        assertThat(BackupPassphraseWrapper.BlobFormat.isValid(bytes)).isTrue()
    }

    // --- rejections -------------------------------------------------------

    @Test
    fun missing_segment_rejected() {
        val bad = b64(ByteArray(16) { 1 }) + "." + b64(ByteArray(12) { 2 })
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun extra_segment_rejected() {
        val bad = goodBlobString() + "." + b64(ByteArray(4))
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun wrong_salt_length_rejected() {
        val bad = b64(ByteArray(8) { 1 }) + "." +
            b64(ByteArray(12) { 2 }) + "." +
            b64(ByteArray(48) { 3 })
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun wrong_iv_length_rejected() {
        val bad = b64(ByteArray(16) { 1 }) + "." +
            b64(ByteArray(16) { 2 }) + "." +
            b64(ByteArray(48) { 3 })
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun empty_ciphertext_rejected() {
        val bad = b64(ByteArray(16) { 1 }) + "." +
            b64(ByteArray(12) { 2 }) + "." +
            b64(ByteArray(0))
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun non_base64_segment_rejected() {
        val bad = "not!base64!chars" + "." + b64(ByteArray(12) { 2 }) + "." + b64(ByteArray(48))
        assertThat(BackupPassphraseWrapper.BlobFormat.parse(bad)).isNull()
    }

    @Test
    fun empty_string_rejected() {
        assertThat(BackupPassphraseWrapper.BlobFormat.parse("")).isNull()
    }

    @Test
    fun random_garbage_rejected() {
        assertThat(BackupPassphraseWrapper.BlobFormat.parse("hello world")).isNull()
    }

    // --- isValid byte\u2010level ----------------------------------------------

    @Test
    fun isValid_rejects_arbitrary_binary_garbage() {
        val garbage = ByteArray(128) { (it * 7).toByte() }
        // We don't care about the exact reason \u2014 just that it is rejected.
        assertThat(BackupPassphraseWrapper.BlobFormat.isValid(garbage)).isFalse()
    }

    @Test
    fun isValid_rejects_empty_bytes() {
        assertThat(BackupPassphraseWrapper.BlobFormat.isValid(ByteArray(0))).isFalse()
    }
}
