package dev.khoj.pitaka.data.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [PdfLibraryRenderer.wrapCell] — the pure word-wrap + line-cap
 * logic behind Title/Author text wrapping. No Android.
 */
class PdfWrapCellTest {

    @Test
    fun short_text_stays_one_line() {
        assertThat(PdfLibraryRenderer.wrapCell(listOf("Short title"), maxChars = 30, maxLines = 3))
            .containsExactly("Short title")
    }

    @Test
    fun long_text_wraps_on_word_boundaries() {
        val wrapped = PdfLibraryRenderer.wrapCell(
            listOf("The Brothers Karamazov and Other Stories"),
            maxChars = 16,
            maxLines = 3,
        )
        // Greedy fill, no word split (all words fit within 16).
        assertThat(wrapped).containsExactly(
            "The Brothers",
            "Karamazov and",
            "Other Stories",
        ).inOrder()
        wrapped.forEach { assertThat(it.length).isAtMost(16) }
    }

    @Test
    fun wrapping_caps_at_max_lines_and_ellipsises() {
        val wrapped = PdfLibraryRenderer.wrapCell(
            listOf("one two three four five six seven eight nine ten eleven twelve"),
            maxChars = 10,
            maxLines = 2,
        )
        assertThat(wrapped).hasSize(2)
        assertThat(wrapped.last()).endsWith("…")
    }

    @Test
    fun single_word_longer_than_limit_is_hard_split() {
        val wrapped = PdfLibraryRenderer.wrapCell(
            listOf("Supercalifragilisticexpialidocious"),
            maxChars = 10,
            maxLines = 5,
        )
        // Each chunk respects the limit; first chunk is exactly 10 chars.
        assertThat(wrapped.first().length).isEqualTo(10)
        wrapped.dropLast(1).forEach { assertThat(it.length).isAtMost(10) }
    }

    @Test
    fun blank_cell_yields_no_lines() {
        assertThat(PdfLibraryRenderer.wrapCell(emptyList(), 20, 3)).isEmpty()
        assertThat(PdfLibraryRenderer.wrapCell(listOf(""), 20, 3)).isEmpty()
    }

    @Test
    fun merged_source_two_logical_lines_each_wrap_then_cap() {
        // Source merge supplies two logical lines (type, detail); with a single
        // wrapLine each they should still both appear when short.
        val wrapped = PdfLibraryRenderer.wrapCell(listOf("Gifted", "Ravi"), maxChars = 20, maxLines = 3)
        assertThat(wrapped).containsExactly("Gifted", "Ravi").inOrder()
    }

    @Test
    fun no_line_ever_exceeds_limit() {
        val wrapped = PdfLibraryRenderer.wrapCell(
            listOf("A reasonably long author name that must wrap nicely across lines"),
            maxChars = 12,
            maxLines = 4,
        )
        wrapped.forEach { assertThat(it.length).isAtMost(12) }
    }
}
