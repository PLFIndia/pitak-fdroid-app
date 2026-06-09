package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import org.junit.Test

/**
 * F-01 (audit): assert [redactForPublish] strips every field the audit
 * flagged and preserves every field the bundled viewer reads.
 *
 * The viewer at `app/src/main/assets/publish/index.html` reads exactly
 * these per-book fields: title, titleTransliteration, author, publisher,
 * publishedYear, isbn, coverUrl.
 */
class PublishRedactionTest {

    private fun sample(
        id: Long = 42L,
        coverUrl: String? = "https://covers.example.com/x.jpg",
        notes: String? = "Lent to my brother in 2023, never returned",
        location: String? = "Living room shelf 3 · row 2",
        addedDate: Long = 1_700_000_000_000L,
        needsMetadata: Boolean = true,
        sourceType: Book.SourceType? = Book.SourceType.GIFT,
        sourceDetail: String? = "from my brother",
    ): Book = Book(
        id = id,
        title = "Tractatus Logico-Philosophicus",
        titleTransliteration = null,
        author = "Wittgenstein",
        isbn = "9780415254083",
        publisher = "Routledge",
        publishedYear = 2001,
        genre = "Philosophy",
        coverUrl = coverUrl,
        pageCount = 224,
        language = "en",
        notes = notes,
        location = location,
        sourceType = sourceType,
        sourceDetail = sourceDetail,
        addedDate = addedDate,
        copyCount = 1,
        needsMetadata = needsMetadata,
    )

    // --- PII stripping -----------------------------------------------------

    @Test
    fun strips_id_notes_location_addedDate_needsMetadata() {
        val redacted = redactForPublish(sample()) { _ -> null }
        assertThat(redacted.id).isEqualTo(0L)
        assertThat(redacted.notes).isNull()
        assertThat(redacted.location).isNull()
        assertThat(redacted.addedDate).isEqualTo(0L)
        assertThat(redacted.needsMetadata).isFalse()
    }

    @Test
    fun strips_source_provenance_fields() {
        val redacted = redactForPublish(sample()) { _ -> null }
        assertThat(redacted.sourceType).isNull()
        assertThat(redacted.sourceDetail).isNull()
    }

    @Test
    fun preserves_viewer_facing_fields() {
        val redacted = redactForPublish(sample()) { _ -> null }
        assertThat(redacted.title).isEqualTo("Tractatus Logico-Philosophicus")
        assertThat(redacted.titleTransliteration).isNull()
        assertThat(redacted.author).isEqualTo("Wittgenstein")
        assertThat(redacted.publisher).isEqualTo("Routledge")
        assertThat(redacted.publishedYear).isEqualTo(2001)
        assertThat(redacted.isbn).isEqualTo("9780415254083")
    }

    @Test
    fun preserves_other_catalog_metadata() {
        val redacted = redactForPublish(sample()) { _ -> null }
        assertThat(redacted.pageCount).isEqualTo(224)
        assertThat(redacted.language).isEqualTo("en")
        assertThat(redacted.genre).isEqualTo("Philosophy")
        assertThat(redacted.copyCount).isEqualTo(1)
    }

    // --- Cover URL rewrite -------------------------------------------------
    //
    // Post-F-09, the caller controls coverUrl entirely. The redactor
    // publishes whatever the callback returns, never the original.

    @Test
    fun coverUrl_is_what_callback_returns() {
        val redacted = redactForPublish(sample(id = 42L)) { b ->
            assertThat(b.id).isEqualTo(42L) // callback sees the real id
            "covers/abc123.jpg"
        }
        assertThat(redacted.coverUrl).isEqualTo("covers/abc123.jpg")
    }

    @Test
    fun coverUrl_is_null_when_callback_returns_null() {
        // F-09: the redactor does NOT silently pass the original URL
        // through. If the caller says "no safe cover", coverUrl is null.
        val redacted = redactForPublish(
            sample(coverUrl = "https://attacker.example/track.gif")
        ) { _ -> null }
        assertThat(redacted.coverUrl).isNull()
    }

    @Test
    fun coverUrl_stays_null_when_book_had_no_cover() {
        val redacted = redactForPublish(sample(coverUrl = null)) { _ -> null }
        assertThat(redacted.coverUrl).isNull()
    }

    // --- Serialisation smoke test (the audit's stated assertion) -----------
    //
    // We don't pull in Moshi here — Book is a plain Kotlin data class and
    // its toString() reflects every field. That is sufficient to catch a
    // regression that re-adds e.g. `notes = "…"` to the published shape.

    @Test
    fun toString_contains_no_sensitive_field_values() {
        val redacted = redactForPublish(
            sample(
                notes = "SECRET-NOTES-MARKER",
                location = "SECRET-LOCATION-MARKER",
                addedDate = 1_700_000_000_000L,
                id = 9999L,
            )
        ) { _ -> "covers/x.jpg" }

        val dump = redacted.toString()
        assertThat(dump).doesNotContain("SECRET-NOTES-MARKER")
        assertThat(dump).doesNotContain("SECRET-LOCATION-MARKER")
        assertThat(dump).doesNotContain("1700000000000")
        assertThat(dump).doesNotContain("9999")
    }
}
