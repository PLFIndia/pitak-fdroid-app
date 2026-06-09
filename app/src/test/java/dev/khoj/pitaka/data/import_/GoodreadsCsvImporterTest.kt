package dev.khoj.pitaka.data.import_

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.WishlistBook
import org.junit.Test

class GoodreadsCsvImporterTest {

    private val sut = GoodreadsCsvImporter()

    /** Builds a Goodreads-style ISBN wrapper: `="9780..."` */
    private fun isbnCell(isbn: String): String = "\"=\"\"$isbn\"\"\""

    @Test
    fun maps_to_read_to_wishlist_and_others_to_library() {
        val header = "Title,Author,ISBN,ISBN13,Publisher,Year Published,Number of Pages,Exclusive Shelf,My Review\n"
        val row1 = "\"Tractatus\",\"Wittgenstein\",${isbnCell("0140428445")},${isbnCell("9780140428445")},\"Penguin\",\"1922\",96,read,\"Loved it.\"\n"
        val row2 = "\"Godaan\",\"Premchand\",${isbnCell("")},${isbnCell("9788121615568")},\"Diamond\",\"1936\",456,to-read,\n"
        val row3 = "\"Sapiens\",\"Harari\",\"\",\"9780062316097\",\"Harper\",\"2014\",443,currently-reading,\n"
        val csv = header + row1 + row2 + row3

        val r = sut.parse(csv)
        assertThat(r.books.map { it.title }).containsExactly("Tractatus", "Sapiens")
        assertThat(r.wishlist.map { it.title }).containsExactly("Godaan")

        val tractatus = r.books.first { it.title == "Tractatus" }
        assertThat(tractatus.author).isEqualTo("Wittgenstein")
        assertThat(tractatus.isbn).isEqualTo("9780140428445") // ISBN13 preferred
        assertThat(tractatus.publisher).isEqualTo("Penguin")
        assertThat(tractatus.publishedYear).isEqualTo(1922)
        assertThat(tractatus.pageCount).isEqualTo(96)

        val godaan = r.wishlist.first()
        assertThat(godaan.priority).isEqualTo(WishlistBook.PRIORITY_MED)
        assertThat(godaan.isbn).isEqualTo("9788121615568")
    }

    @Test
    fun missing_title_rows_collected_as_errors_not_thrown() {
        val header = "Title,Author,ISBN,ISBN13,Publisher,Year Published,Number of Pages,Exclusive Shelf\n"
        val row1 = "\"\",\"Anon\",\"\",\"\",\"\",\"\",,read\n"
        val row2 = "\"Good\",\"\",,,,,,to-read\n"
        val csv = header + row1 + row2

        val r = sut.parse(csv)
        assertThat(r.books).isEmpty()
        assertThat(r.wishlist.map { it.title }).containsExactly("Good")
        assertThat(r.parseErrors).hasSize(1)
        assertThat(r.parseErrors.first()).contains("missing title")
    }

    @Test
    fun non_goodreads_csv_rejected_cleanly() {
        val csv = "foo,bar,baz\n1,2,3\n"
        val r = sut.parse(csv)
        assertThat(r.books).isEmpty()
        assertThat(r.wishlist).isEmpty()
        assertThat(r.parseErrors).hasSize(1)
        assertThat(r.parseErrors.first()).contains("Not a Goodreads CSV")
    }

    @Test
    fun isbn_normalization_strips_dashes() {
        val header = "Title,Author,ISBN,ISBN13,Publisher,Year Published,Number of Pages,Exclusive Shelf\n"
        val row = "\"X\",\"Y\",${isbnCell("978-0-14-042844-5")},\"\",\"\",\"\",,read\n"
        val csv = header + row
        val r = sut.parse(csv)
        assertThat(r.books.first().isbn).isEqualTo("9780140428445")
    }
}
