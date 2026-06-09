package dev.khoj.pitaka.data.import_

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvParserTest {

    @Test
    fun simple_rfc4180_parses() {
        val rows = parseCsv("a,b,c\n1,2,3\n")
        assertThat(rows).hasSize(2)
        assertThat(rows[0]).containsExactly("a", "b", "c").inOrder()
        assertThat(rows[1]).containsExactly("1", "2", "3").inOrder()
    }

    @Test
    fun quoted_fields_keep_commas() {
        val rows = parseCsv("title,author\n\"He said, hi\",me\n")
        assertThat(rows[1]).containsExactly("He said, hi", "me").inOrder()
    }

    @Test
    fun doubled_quotes_unescape() {
        val rows = parseCsv("\"He said \"\"hi\"\"\",me\n")
        assertThat(rows[0]).containsExactly("He said \"hi\"", "me").inOrder()
    }

    @Test
    fun handles_no_trailing_newline() {
        val rows = parseCsv("a,b")
        assertThat(rows).hasSize(1)
        assertThat(rows[0]).containsExactly("a", "b").inOrder()
    }

    @Test
    fun handles_crlf_line_endings() {
        val rows = parseCsv("a,b\r\nc,d\r\n")
        assertThat(rows).hasSize(2)
        assertThat(rows[0]).containsExactly("a", "b").inOrder()
        assertThat(rows[1]).containsExactly("c", "d").inOrder()
    }

    @Test
    fun empty_fields_remain_empty() {
        val rows = parseCsv("a,,b\n")
        assertThat(rows[0]).containsExactly("a", "", "b").inOrder()
    }
}
