package dev.khoj.pitaka.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks the library-ID string limitation (PLAN-merge.md D40): 16–64 chars,
 * lowercase hex. This is the single source of truth every adopt/persist path
 * funnels through (QR pairing, file merge, regenerate, AppPreferences).
 */
class LibraryIdTest {

    @Test
    fun accepts_minted_shapes() {
        // 32-char hex (16 random bytes) — the SecureRandom mint.
        assertThat(LibraryId.isValid("a".repeat(32))).isTrue()
        assertThat(LibraryId.isValid("0123456789abcdef0123456789abcdef")).isTrue()
        // Hyphen-stripped UUID (regenerate) is 32 lowercase hex.
        assertThat(LibraryId.isValid("0123456789abcdef0123456789abcdef")).isTrue()
        // Boundary lengths.
        assertThat(LibraryId.isValid("a".repeat(16))).isTrue()
        assertThat(LibraryId.isValid("a".repeat(64))).isTrue()
    }

    @Test
    fun rejects_out_of_bounds_lengths() {
        assertThat(LibraryId.isValid("")).isFalse()
        assertThat(LibraryId.isValid("a".repeat(15))).isFalse()
        assertThat(LibraryId.isValid("a".repeat(65))).isFalse()
        assertThat(LibraryId.isValid("a".repeat(9000))).isFalse()
    }

    @Test
    fun rejects_non_lowercase_hex() {
        assertThat(LibraryId.isValid("A".repeat(32))).isFalse()        // uppercase
        assertThat(LibraryId.isValid("g".repeat(32))).isFalse()        // non-hex letter
        assertThat(LibraryId.isValid("0123-4567-89ab-cdef-0123")).isFalse() // hyphens
        assertThat(LibraryId.isValid("'; DROP TABLE books;--".repeat(2))).isFalse() // injection junk
        assertThat(LibraryId.isValid("z".repeat(16))).isFalse()
    }

    @Test
    fun normalizeOrNull_trims_valid_and_nulls_invalid() {
        val id = "a".repeat(32)
        assertThat(LibraryId.normalizeOrNull("  $id  ")).isEqualTo(id)
        assertThat(LibraryId.normalizeOrNull("")).isNull()
        assertThat(LibraryId.normalizeOrNull("LIB-A")).isNull()
        assertThat(LibraryId.normalizeOrNull("Z".repeat(9000))).isNull()
    }
}
