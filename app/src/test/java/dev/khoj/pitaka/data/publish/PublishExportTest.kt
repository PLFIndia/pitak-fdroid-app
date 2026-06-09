package dev.khoj.pitaka.data.publish

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.domain.model.Book
import org.junit.Test

/**
 * The world-facing publish payload must (a) round-trip through reflective Moshi
 * exactly like the app's NetworkModule configures it, (b) carry ONLY coarse
 * availability — never an exact loaned/available count (Q1=C), and (c) never
 * leak the PII that redactForPublish strips.
 */
class PublishExportTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun publishExport_round_trips_with_coarse_availability() {
        val export = PublishExport(
            exportedAt = 123L,
            books = listOf(
                PublishBook(title = "Dune", author = "Herbert", availability = PublishBook.AVAILABLE),
                PublishBook(title = "1984", author = "Orwell", availability = PublishBook.OUT),
                PublishBook(title = "Unknown", availability = null),
            ),
        )
        val json = moshi.adapter<PublishExport>().toJson(export)
        val parsed = moshi.adapter<PublishExport>().fromJson(json)!!
        assertThat(parsed.schemaVersion).isEqualTo(1)
        assertThat(parsed.books.map { it.availability })
            .containsExactly("available", "out", null).inOrder()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun fromRedacted_carries_only_catalog_fields_plus_availability() {
        // A redacted book has id/notes/location/source/addedDate already zeroed.
        val redacted = Book(
            id = 0L,
            title = "Sapiens",
            author = "Harari",
            isbn = "9780099590088",
            publisher = "Vintage",
            publishedYear = 2014,
            genre = "History",
            language = "English",
            coverUrl = "covers/abc.jpg",
            notes = null,
            location = null,
            sourceType = null,
            sourceDetail = null,
        )
        val pb = PublishBook.fromRedacted(redacted, PublishBook.OUT)
        assertThat(pb.title).isEqualTo("Sapiens")
        assertThat(pb.genre).isEqualTo("History")
        assertThat(pb.language).isEqualTo("English")
        assertThat(pb.availability).isEqualTo("out")

        // The serialized JSON must not contain any exact count or PII field key.
        val json = moshi.adapter<PublishBook>().toJson(pb)
        assertThat(json).doesNotContain("copyCount")
        assertThat(json).doesNotContain("sourceType")
        assertThat(json).doesNotContain("sourceDetail")
        assertThat(json).doesNotContain("location")
        assertThat(json).doesNotContain("notes")
        assertThat(json).doesNotContain("addedDate")
    }
}
