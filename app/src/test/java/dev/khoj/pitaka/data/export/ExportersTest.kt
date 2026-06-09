package dev.khoj.pitaka.data.export

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook
import org.junit.Test

class ExportersTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val sut = Exporters(moshi)

    private val sampleBook = Book(
        id = 1L,
        title = "Tractatus Logico-Philosophicus",
        author = "Ludwig Wittgenstein",
        isbn = "9780140428445",
        publisher = "Penguin Classics",
        publishedYear = 1922,
        pageCount = 96,
        location = "Shelf 1, row 2",
        addedDate = 1_700_000_000_000L,
        copyCount = 1,
    )

    private val sampleWishlist = WishlistBook(
        id = 2L,
        title = "Godaan",
        titleTransliteration = "Godaan",
        author = "Premchand",
        priority = WishlistBook.PRIORITY_HIGH,
        addedDate = 1_700_000_000_001L,
    )

    // --- JSON ---

    @Test
    fun toJson_emits_schemaVersion_field() {
        val payload = PitakaExport(exportedAt = 0L, books = listOf(sampleBook), wishlist = listOf(sampleWishlist))
        val json = sut.toJson(payload)
        assertThat(json).contains("\"schemaVersion\": 3")
        assertThat(json).contains("Tractatus Logico-Philosophicus")
        assertThat(json).contains("Godaan")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun toJson_roundtrips_via_PitakaJsonImporter_shape() {
        val payload = PitakaExport(exportedAt = 0L, books = listOf(sampleBook), wishlist = listOf(sampleWishlist))
        val json = sut.toJson(payload)
        // Validate using Moshi's adapter directly — equivalent to PitakaJsonImporter's parse.
        val parsed = moshi.adapter<PitakaExport>().fromJson(json)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.books).hasSize(1)
        assertThat(parsed.books.first().title).isEqualTo("Tractatus Logico-Philosophicus")
        assertThat(parsed.wishlist.first().priority).isEqualTo(WishlistBook.PRIORITY_HIGH)
    }

    @Test
    fun writeJson_streams_identical_output_to_toJson() {
        val payload = PitakaExport(exportedAt = 0L, books = listOf(sampleBook), wishlist = listOf(sampleWishlist))
        val out = java.io.ByteArrayOutputStream()
        sut.writeJson(payload, out)
        val streamed = out.toString("UTF-8")
        // The streaming writer (P6) must produce byte-identical output to the
        // in-memory toJson so importers and git-diff behaviour are unchanged.
        assertThat(streamed).isEqualTo(sut.toJson(payload))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun writeJson_output_roundtrips() {
        val payload = PitakaExport(exportedAt = 0L, books = listOf(sampleBook), wishlist = listOf(sampleWishlist))
        val out = java.io.ByteArrayOutputStream()
        sut.writeJson(payload, out)
        val parsed = moshi.adapter<PitakaExport>().fromJson(out.toString("UTF-8"))
        assertThat(parsed!!.books.first().title).isEqualTo("Tractatus Logico-Philosophicus")
    }

    // --- CSV ---

    @Test
    fun libraryToCsv_emits_header_then_one_row_per_book() {
        val csv = sut.libraryToCsv(listOf(sampleBook))
        val lines = csv.lines().filter { it.isNotBlank() }
        assertThat(lines).hasSize(2)
        assertThat(lines.first()).isEqualTo(Exporters.LIBRARY_CSV_HEADER)
        assertThat(lines[1]).contains("Tractatus Logico-Philosophicus")
        assertThat(lines[1]).contains("9780140428445")
    }

    @Test
    fun csv_escapes_commas_and_quotes() {
        val tricky = sampleBook.copy(title = "He said, \"hello\"", author = "A, B")
        val csv = sut.libraryToCsv(listOf(tricky))
        // The title column should be quoted and have its quotes doubled.
        assertThat(csv).contains("\"He said, \"\"hello\"\"\"")
        // The author should be quoted because it contains a comma.
        assertThat(csv).contains("\"A, B\"")
    }

    @Test
    fun csv_handles_empty_list() {
        val csv = sut.libraryToCsv(emptyList())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertThat(lines).containsExactly(Exporters.LIBRARY_CSV_HEADER)
    }

    @Test
    fun csv_includes_source_type_and_detail_columns() {
        val withSource = sampleBook.copy(
            sourceType = Book.SourceType.GIFT,
            sourceDetail = "from Ravi",
        )
        val csv = sut.libraryToCsv(listOf(withSource))
        val lines = csv.lines().filter { it.isNotBlank() }
        // Header carries the two new columns.
        assertThat(lines.first()).contains("sourceType,sourceDetail")
        // Row carries the enum name and the free-form detail.
        assertThat(lines[1]).contains("GIFT")
        assertThat(lines[1]).contains("from Ravi")
    }

    @Test
    fun wishlistToCsv_includes_priority_and_purchased() {
        val csv = sut.wishlistToCsv(listOf(sampleWishlist))
        assertThat(csv).contains(Exporters.WISHLIST_CSV_HEADER)
        assertThat(csv).contains("Godaan")
        assertThat(csv).contains(",${WishlistBook.PRIORITY_HIGH},")
    }
}
