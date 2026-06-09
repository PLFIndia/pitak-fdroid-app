package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.export.Exporters
import dev.khoj.pitaka.data.export.PdfColumn
import dev.khoj.pitaka.data.export.PdfExportAssets
import dev.khoj.pitaka.data.export.PdfLibraryRenderer
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Guards the export contract surfaced by the share-sheet rework:
 *  1. Filenames carry the `pitak-` prefix (renamed from `pitaka-`). The share
 *     flow streams to a cache file named by [ExportUseCase.Result.filename], so
 *     a regression here is user-visible in the share sheet.
 *  2. JSON `writeTo` still streams a body that round-trips through Moshi (the P6
 *     streaming invariant must survive the OutputStream swap from SAF to cache).
 */
class ExportUseCaseTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val sampleBook = Book(
        id = 1L,
        title = "Tractatus Logico-Philosophicus",
        author = "Ludwig Wittgenstein",
        isbn = "9780140428445",
        addedDate = 1_700_000_000_000L,
        copyCount = 1,
    )

    private val sampleWishlist = WishlistBook(
        id = 2L,
        title = "Godaan",
        author = "Premchand",
        priority = WishlistBook.PRIORITY_HIGH,
        addedDate = 1_700_000_000_001L,
    )

    private fun useCase(): ExportUseCase {
        val bookRepo = mockk<BookRepository> {
            every { observeAll(any(), any()) } returns flowOf(listOf(sampleBook))
        }
        val wishlistRepo = mockk<WishlistRepository> {
            every { observeActive(any()) } returns flowOf(listOf(sampleWishlist))
            every { observePurchased() } returns flowOf(emptyList())
        }
        val prefs = mockk<AppPreferences> {
            coEvery { getOrCreateLibraryId() } returns "lib-123"
            every { libraryName() } returns flowOf("My Library")
            every { pdfColumns() } returns flowOf(PdfColumn.DEFAULT)
            every { libraryLogoUri() } returns flowOf("")
        }
        return ExportUseCase(
            bookRepo = bookRepo,
            wishlistRepo = wishlistRepo,
            exporters = Exporters(moshi),
            bundle = mockk<dev.khoj.pitaka.data.bundle.LibraryBundle>(relaxed = true),
            pdfRenderer = mockk<PdfLibraryRenderer>(relaxed = true),
            pdfAssets = mockk<PdfExportAssets>(relaxed = true),
            prefs = prefs,
            clock = { 1_700_000_000_000L },
        )
    }

    @Test
    fun json_filename_uses_pitak_prefix() = runBlocking {
        val r = useCase().invoke(ExportUseCase.Scope.Both, ExportUseCase.Format.Json)
        assertThat(r.filename).isEqualTo("pitak-1700000000000.json")
        assertThat(r.mime).isEqualTo("application/json")
    }

    @Test
    fun csv_pdf_bundle_filenames_use_pitak_prefix() = runBlocking {
        val uc = useCase()
        assertThat(uc(ExportUseCase.Scope.Both, ExportUseCase.Format.Csv).filename)
            .isEqualTo("pitak-1700000000000.csv")
        assertThat(uc(ExportUseCase.Scope.LibraryOnly, ExportUseCase.Format.Pdf).filename)
            .isEqualTo("pitak-1700000000000.pdf")
        val bundle = uc(ExportUseCase.Scope.Both, ExportUseCase.Format.Bundle)
        assertThat(bundle.filename).isEqualTo("pitak-1700000000000.zip")
        assertThat(bundle.mime).isEqualTo("application/zip")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun json_writeTo_streams_a_roundtrippable_body() = runBlocking {
        val r = useCase().invoke(ExportUseCase.Scope.Both, ExportUseCase.Format.Json)
        val out = ByteArrayOutputStream()
        r.writeTo(out)
        val parsed = moshi.adapter<PitakaExport>().fromJson(out.toString("UTF-8"))
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.books.first().title).isEqualTo("Tractatus Logico-Philosophicus")
        assertThat(parsed.wishlist.first().title).isEqualTo("Godaan")
        assertThat(parsed.libraryId).isEqualTo("lib-123")
    }
}
