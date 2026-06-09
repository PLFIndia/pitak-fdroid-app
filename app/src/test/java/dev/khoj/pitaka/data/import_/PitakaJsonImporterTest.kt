package dev.khoj.pitaka.data.import_

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.domain.model.Book
import org.junit.Test

/**
 * Wave 3 (PLAN-covers.md D3): the JSON importer must reset book ids AND drop
 * local cover references, because JSON carries no image bytes. A local
 * `covers/<uuid>.jpg` or legacy `file://…` path would otherwise land on a
 * freshly-reassigned id and point at a file that does not exist on this device
 * — the original "same cover on two books" cross-wiring. Remote https covers
 * resolve anywhere, so they pass through.
 */
class PitakaJsonImporterTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val importer = PitakaJsonImporter(moshi)

    @OptIn(ExperimentalStdlibApi::class)
    private fun jsonOf(vararg books: Book): String {
        val export = PitakaExport(exportedAt = 1L, books = books.toList(), wishlist = emptyList())
        return moshi.adapter<PitakaExport>().toJson(export)
    }

    @Test
    fun `relative local cover is nulled on import`() {
        val payload = importer.parse(
            jsonOf(Book(id = 42L, title = "Kabir", coverUrl = "covers/abc-123.jpg")),
        )
        assertThat(payload.books).hasSize(1)
        assertThat(payload.books[0].coverUrl).isNull()
    }

    @Test
    fun `legacy file uri cover is nulled on import`() {
        val payload = importer.parse(
            jsonOf(Book(title = "T", coverUrl = "file:///data/user/0/x/files/covers/7.jpg")),
        )
        assertThat(payload.books[0].coverUrl).isNull()
    }

    @Test
    fun `remote https cover passes through unchanged`() {
        val url = "https://covers.openlibrary.org/b/id/12345-M.jpg"
        val payload = importer.parse(jsonOf(Book(title = "T", coverUrl = url)))
        assertThat(payload.books[0].coverUrl).isEqualTo(url)
    }

    @Test
    fun `null cover stays null`() {
        val payload = importer.parse(jsonOf(Book(title = "T", coverUrl = null)))
        assertThat(payload.books[0].coverUrl).isNull()
    }

    @Test
    fun `id is reset to zero so room reassigns`() {
        val payload = importer.parse(jsonOf(Book(id = 99L, title = "T")))
        assertThat(payload.books[0].id).isEqualTo(0L)
    }

    @Test
    fun `mixed payload nulls only local covers`() {
        val payload = importer.parse(
            jsonOf(
                Book(title = "local", coverUrl = "covers/u1.jpg"),
                Book(title = "remote", coverUrl = "https://books.google.com/x?id=1"),
                Book(title = "none", coverUrl = null),
            ),
        )
        assertThat(payload.books.map { it.coverUrl }).containsExactly(
            null,
            "https://books.google.com/x?id=1",
            null,
        ).inOrder()
    }

    @Test
    fun `non-cover fields survive the import`() {
        val payload = importer.parse(
            jsonOf(Book(title = "Title", author = "Auth", isbn = "9780000000001", coverUrl = "covers/x.jpg")),
        )
        val b = payload.books[0]
        assertThat(b.title).isEqualTo("Title")
        assertThat(b.author).isEqualTo("Auth")
        assertThat(b.isbn).isEqualTo("9780000000001")
        assertThat(b.coverUrl).isNull()
    }
}
