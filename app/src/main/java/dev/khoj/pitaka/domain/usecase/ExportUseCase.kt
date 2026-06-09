package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.bundle.LibraryBundle
import dev.khoj.pitaka.data.export.Exporters
import dev.khoj.pitaka.data.export.PdfColumnPlan
import dev.khoj.pitaka.data.export.PdfExportAssets
import dev.khoj.pitaka.data.export.PdfLibraryRenderer
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.first
import java.io.OutputStream
import javax.inject.Inject

class ExportUseCase(
    private val bookRepo: BookRepository,
    private val wishlistRepo: WishlistRepository,
    private val exporters: Exporters,
    private val bundle: LibraryBundle,
    private val pdfRenderer: PdfLibraryRenderer,
    private val pdfAssets: PdfExportAssets,
    private val prefs: AppPreferences,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        bookRepo: BookRepository,
        wishlistRepo: WishlistRepository,
        exporters: Exporters,
        bundle: LibraryBundle,
        pdfRenderer: PdfLibraryRenderer,
        pdfAssets: PdfExportAssets,
        prefs: AppPreferences,
    ) : this(bookRepo, wishlistRepo, exporters, bundle, pdfRenderer, pdfAssets, prefs, clock = System::currentTimeMillis)

    /**
     * [writeTo] streams the export to the destination OutputStream at save time.
     * Data is fetched and serialized inside [writeTo] — nothing large is held in
     * memory between preparing the export and the user picking a destination
     * (P6). JSON streams via Moshi's JsonWriter; CSV builds a String (smaller,
     * and the streaming win is JSON-specific). Bundle wraps the JSON + local
     * cover files in a ZIP.
     */
    data class Result(
        val filename: String,
        val mime: String,
        val writeTo: suspend (OutputStream) -> Unit,
    )

    enum class Scope { LibraryOnly, WishlistOnly, Both }
    enum class Format { Json, Csv, Bundle, Pdf }

    suspend operator fun invoke(scope: Scope, format: Format): Result {
        val stamp = clock()
        return when (format) {
            Format.Json -> Result(
                filename = "pitak-$stamp.json",
                mime = "application/json",
                writeTo = { out ->
                    exporters.writeJson(buildExport(scope, stamp), out)
                },
            )
            Format.Bundle -> Result(
                // Pitaka bundle: the canonical JSON plus the actual local cover
                // images, no vault data. Self-contained library interchange.
                filename = "pitak-$stamp.zip",
                mime = "application/zip",
                writeTo = { out ->
                    bundle.writeTo(buildExport(scope, stamp), out)
                },
            )
            Format.Csv -> Result(
                filename = "pitak-$stamp.csv",
                mime = "text/csv",
                writeTo = { out ->
                    val body = when (scope) {
                        Scope.LibraryOnly  -> exporters.libraryToCsv(loadLibrary(scope))
                        Scope.WishlistOnly -> exporters.wishlistToCsv(loadWishlist(scope))
                        Scope.Both         -> exporters.libraryToCsv(loadLibrary(scope)) +
                                "\n\n" + exporters.wishlistToCsv(loadWishlist(scope))
                    }
                    out.write(body.toByteArray(Charsets.UTF_8))
                },
            )
            Format.Pdf -> Result(
                // Feature 1: PDF of the book list. Always the LIBRARY list
                // (a PDF "book list" is the catalog), regardless of scope —
                // the wishlist has its own export formats. Library name + logo
                // in the header, Pitak attribution footer, user-selected columns.
                filename = "pitak-$stamp.pdf",
                mime = "application/pdf",
                writeTo = { out ->
                    val name = prefs.libraryName().first().ifBlank { "My Library" }
                    val selectedColumns = prefs.pdfColumns().first()
                    val columns = PdfColumnPlan.resolve(selectedColumns, pdfAssets.labels())
                    val logo = PdfLibraryRenderer.decodeLogo(prefs.libraryLogoUri().first())
                    pdfRenderer.render(
                        libraryName = name,
                        books = bookRepo.observeAll().first(),
                        columns = columns,
                        logo = logo,
                        footerIcon = pdfAssets.footerIcon(),
                        footerText = pdfAssets.footerText,
                        out = out,
                    )
                },
            )
        }
    }

    /**
     * Builds the canonical [PitakaExport] for [scope]. Shared by the JSON and
     * Bundle formats — both ship the exact same D4-clean payload (books +
     * wishlist + library namespace, no vault data); the bundle just additionally
     * carries the referenced local cover files.
     */
    private suspend fun buildExport(scope: Scope, stamp: Long) = PitakaExport(
        exportedAt = stamp,
        books = loadLibrary(scope),
        wishlist = loadWishlist(scope),
        // Library namespace (D40): stamp WHICH library this file belongs to so a
        // recipient's merge gate can match IDs. getOrCreate ensures every export
        // carries one.
        libraryId = prefs.getOrCreateLibraryId(),
        libraryName = prefs.libraryName().first(),
    )

    private suspend fun loadLibrary(scope: Scope) =
        if (scope == Scope.WishlistOnly) emptyList() else bookRepo.observeAll().first()

    private suspend fun loadWishlist(scope: Scope) =
        if (scope == Scope.LibraryOnly) emptyList()
        else wishlistRepo.observeActive().first() + wishlistRepo.observePurchased().first()
}
