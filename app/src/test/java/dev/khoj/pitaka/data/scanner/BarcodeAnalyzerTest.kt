package dev.khoj.pitaka.data.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodeAnalyzerTest {

    @Test
    fun rejects_null_rawValue() {
        val sut = BarcodeAnalyzer()
        assertThat(sut.analyze(null, BarcodeAnalyzer.EAN_13_FORMAT))
            .isEqualTo(BarcodeDecision.Reject)
    }

    @Test
    fun rejects_non_EAN_13_format() {
        val sut = BarcodeAnalyzer()
        val notEan = 999
        val r = sut.analyze("9780140428445", notEan)
        assertThat(r).isEqualTo(BarcodeDecision.Reject)
    }

    @Test
    fun rejects_non_13_digit_strings() {
        val sut = BarcodeAnalyzer()
        assertThat(sut.analyze("978014044", BarcodeAnalyzer.EAN_13_FORMAT))
            .isEqualTo(BarcodeDecision.Reject)
        assertThat(sut.analyze("97801404284450000", BarcodeAnalyzer.EAN_13_FORMAT))
            .isEqualTo(BarcodeDecision.Reject)
    }

    @Test
    fun rejects_EAN_without_Bookland_prefix() {
        val sut = BarcodeAnalyzer()
        // 5 011053 050706 is a typical product EAN-13 (not a book).
        val r = sut.analyze("5011053050706", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(r).isEqualTo(BarcodeDecision.Reject)
    }

    @Test
    fun accepts_978_prefix_ISBN() {
        val sut = BarcodeAnalyzer()
        val r = sut.analyze("9780140428445", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(r).isEqualTo(BarcodeDecision.Accept("9780140428445"))
    }

    @Test
    fun accepts_979_prefix_ISBN() {
        val sut = BarcodeAnalyzer()
        val r = sut.analyze("9791234567896", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(r).isEqualTo(BarcodeDecision.Accept("9791234567896"))
    }

    @Test
    fun strips_non_digit_characters_before_validation() {
        val sut = BarcodeAnalyzer()
        // ML Kit sometimes returns the digits with separators.
        val r = sut.analyze("978-0-14-042844-5", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(r).isEqualTo(BarcodeDecision.Accept("9780140428445"))
    }

    @Test
    fun debounces_repeated_scans_within_window() {
        var now = 1_000L
        val sut = BarcodeAnalyzer(debounceMillis = 2_000L, clock = { now })

        val first = sut.analyze("9780140428445", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(first).isInstanceOf(BarcodeDecision.Accept::class.java)

        // 1 second later — same code, still in window.
        now += 1_000L
        val dupe = sut.analyze("9780140428445", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(dupe).isEqualTo(BarcodeDecision.Reject)

        // After the window expires, the same code is accepted again.
        now += 1_500L
        val again = sut.analyze("9780140428445", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(again).isInstanceOf(BarcodeDecision.Accept::class.java)
    }

    @Test
    fun different_isbns_are_not_debounced_against_each_other() {
        var now = 1_000L
        val sut = BarcodeAnalyzer(debounceMillis = 2_000L, clock = { now })

        sut.analyze("9780140428445", BarcodeAnalyzer.EAN_13_FORMAT)
        now += 100L
        val other = sut.analyze("9791234567896", BarcodeAnalyzer.EAN_13_FORMAT)
        assertThat(other).isEqualTo(BarcodeDecision.Accept("9791234567896"))
    }
}
