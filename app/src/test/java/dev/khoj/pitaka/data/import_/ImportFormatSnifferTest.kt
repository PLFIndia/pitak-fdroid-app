package dev.khoj.pitaka.data.import_

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportFormatSnifferTest {

    private val sut = ImportFormatSniffer()

    @Test
    fun pitaka_json_detected() {
        val text = """{ "schemaVersion": 1, "exportedAt": 0, "books": [], "wishlist": [] }"""
        assertThat(sut.detect(text)).isEqualTo(ImportFormat.PitakaJson)
    }

    @Test
    fun goodreads_csv_detected_by_exclusive_shelf_column() {
        val text = "Title,Author,Exclusive Shelf\nFoo,Bar,read\n"
        assertThat(sut.detect(text)).isEqualTo(ImportFormat.GoodreadsCsv)
    }

    @Test
    fun garbage_returns_null() {
        assertThat(sut.detect("just some random text")).isNull()
    }

    @Test
    fun csv_without_goodreads_columns_rejected() {
        // Looks like a CSV but isn't Goodreads — we'd rather refuse than guess.
        assertThat(sut.detect("foo,bar,baz\n1,2,3\n")).isNull()
    }
}
