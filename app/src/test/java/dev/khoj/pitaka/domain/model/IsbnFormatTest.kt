package dev.khoj.pitaka.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IsbnFormatTest {

    // --- accepted ISBN-13 ---

    @Test
    fun accepts_valid_isbn13() {
        // 9780140328721 — Fantastic Mr Fox, real valid ISBN-13.
        assertThat(IsbnFormat.isValid("9780140328721")).isTrue()
    }

    @Test
    fun accepts_valid_isbn13_with_zero_check_digit() {
        // 9783161484100 — classic example ISBN-13 with check digit 0.
        assertThat(IsbnFormat.isValid("9783161484100")).isTrue()
    }

    // --- accepted ISBN-10 ---

    @Test
    fun accepts_valid_isbn10() {
        // 0140328726 — a real, check-digit-valid ISBN-10.
        assertThat(IsbnFormat.isValid("0140328726")).isTrue()
    }

    @Test
    fun accepts_valid_isbn10_with_x_check_digit() {
        // 080442957X — real ISBN-10 whose check digit is X (value 10).
        assertThat(IsbnFormat.isValid("080442957X")).isTrue()
    }

    // --- rejected: wrong length ---

    @Test
    fun rejects_short_placeholder() {
        // The string used throughout the lookup tests as a stand-in.
        assertThat(IsbnFormat.isValid("978")).isFalse()
    }

    @Test
    fun rejects_empty() {
        assertThat(IsbnFormat.isValid("")).isFalse()
    }

    @Test
    fun rejects_twelve_digits() {
        assertThat(IsbnFormat.isValid("978014032872")).isFalse()
    }

    @Test
    fun rejects_fourteen_digits() {
        assertThat(IsbnFormat.isValid("97801403287211")).isFalse()
    }

    // --- rejected: bad check digit ---

    @Test
    fun rejects_isbn13_with_wrong_check_digit() {
        // Last digit flipped 1 -> 2.
        assertThat(IsbnFormat.isValid("9780140328722")).isFalse()
    }

    @Test
    fun rejects_isbn10_with_wrong_check_digit() {
        assertThat(IsbnFormat.isValid("0140328722")).isFalse()
    }

    @Test
    fun rejects_isbn13_transposition() {
        // Transpose two adjacent digits — the weighted sum catches it.
        assertThat(IsbnFormat.isValid("9780140327821")).isFalse()
    }

    // --- rejected: bad character class ---

    @Test
    fun rejects_isbn13_with_letter() {
        assertThat(IsbnFormat.isValid("97801403287AX")).isFalse()
    }

    @Test
    fun rejects_isbn10_with_x_not_in_last_position() {
        assertThat(IsbnFormat.isValid("X140328721")).isFalse()
    }

    @Test
    fun rejects_lowercase_x_check_digit() {
        // normalize() uppercases, so a lowercase x means un-normalised input —
        // reject it rather than silently accept a non-canonical form.
        assertThat(IsbnFormat.isValid("080442957x")).isFalse()
    }

    @Test
    fun rejects_garbage_string() {
        assertThat(IsbnFormat.isValid("not-an-isbn")).isFalse()
    }
}
