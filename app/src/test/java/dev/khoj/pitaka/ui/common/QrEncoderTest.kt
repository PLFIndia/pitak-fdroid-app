package dev.khoj.pitaka.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrEncoderTest {

    @Test
    fun libraryIdPayload_roundtrips_through_parse() {
        val id = "a1b2c3d4e5f6a1b2" // 16-char hex
        val payload = QrEncoder.libraryIdPayload(id)
        assertThat(payload).isEqualTo("pitaka-lib:a1b2c3d4e5f6a1b2")
        assertThat(QrEncoder.parseLibraryIdPayload(payload)).isEqualTo(id)
    }

    @Test
    fun parse_rejects_non_pitaka_qr() {
        assertThat(QrEncoder.parseLibraryIdPayload("https://example.com")).isNull()
        assertThat(QrEncoder.parseLibraryIdPayload("9780140449136")).isNull()
        assertThat(QrEncoder.parseLibraryIdPayload("pitaka-lib:")).isNull()
        assertThat(QrEncoder.parseLibraryIdPayload("")).isNull()
    }

    @Test
    fun parse_rejects_valid_prefix_but_invalid_id_shape() {
        // Prefix present but the ID isn't hex / is too short / has bad chars.
        assertThat(QrEncoder.parseLibraryIdPayload("pitaka-lib:hello")).isNull()      // not hex
        assertThat(QrEncoder.parseLibraryIdPayload("pitaka-lib:abc")).isNull()        // too short
        assertThat(QrEncoder.parseLibraryIdPayload("pitaka-lib:ABCDEF0123456789")).isNull() // uppercase
        assertThat(QrEncoder.parseLibraryIdPayload("pitaka-lib:0123456789abcdefXY")).isNull() // bad chars
    }

    @Test
    fun isValidLibraryId_accepts_minted_shapes() {
        // 32-char SecureRandom hex (getOrCreate) and hyphen-stripped UUID (regenerate).
        assertThat(QrEncoder.isValidLibraryId("0123456789abcdef0123456789abcdef")).isTrue()
        assertThat(QrEncoder.isValidLibraryId("a".repeat(32))).isTrue()
        assertThat(QrEncoder.isValidLibraryId("abc")).isFalse()
        assertThat(QrEncoder.isValidLibraryId("a".repeat(65))).isFalse()
    }

    @Test
    fun parse_tolerates_surrounding_whitespace() {
        val id = "0123456789abcdef"
        assertThat(QrEncoder.parseLibraryIdPayload("  pitaka-lib:$id  ")).isEqualTo(id)
    }
}
