package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.import_.GoodreadsCsvImporter
import dev.khoj.pitaka.data.import_.ImportFormat
import dev.khoj.pitaka.data.import_.ImportFormatSniffer
import dev.khoj.pitaka.data.import_.ImportPayload
import dev.khoj.pitaka.data.import_.PitakaJsonImporter
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Inject

/**
 * One-shot import. Sniffs format, parses, writes through both repositories,
 * returns a clean summary.
 *
 * Duplicates: existing ISBNs are *skipped* on the library side; on the
 * wishlist side, an existing ISBN replaces the row (latest wins). This
 * keeps re-imports idempotent and matches the D2 spirit ("you already own
 * this — do something explicit") without forcing dialog spam on a 500-book
 * import.
 */
class ImportLibraryUseCase @Inject constructor(
    private val sniffer: ImportFormatSniffer,
    private val pitakaJson: PitakaJsonImporter,
    private val goodreads: GoodreadsCsvImporter,
    private val bookRepo: BookRepository,
    private val wishlistRepo: WishlistRepository,
) {
    data class Summary(
        val format: ImportFormat?,
        val booksAdded: Int,
        val booksSkipped: Int,
        val wishlistAdded: Int,
        val wishlistReplaced: Int,
        val parseErrors: List<String>,
    ) {
        val isFailure: Boolean get() = format == null
    }

    suspend operator fun invoke(text: String): Summary {
        val format = sniffer.detect(text)
            ?: return Summary(null, 0, 0, 0, 0, listOf("Unrecognized file format."))

        val payload: ImportPayload = when (format) {
            ImportFormat.PitakaJson  -> pitakaJson.parse(text)
            ImportFormat.GoodreadsCsv -> goodreads.parse(text)
            // A bundle is a ZIP, never plain text — it is imported via
            // [apply] from the bundle reader, not through this text entry point.
            ImportFormat.PitakaBundle ->
                return Summary(null, 0, 0, 0, 0, listOf("Bundle files are imported as a ZIP, not as text."))
        }

        return apply(payload, format)
    }

    /**
     * Writes an already-parsed payload through both repositories and returns a
     * summary. Shared by the text path ([invoke]) and the bundle path (the
     * caller parses the ZIP, then hands the payload here with
     * [ImportFormat.PitakaBundle]). Idempotent on re-import: existing ISBNs are
     * skipped on the library side, replaced (latest-wins) on the wishlist side.
     */
    suspend fun apply(payload: ImportPayload, format: ImportFormat): Summary {
        var booksAdded = 0
        var booksSkipped = 0
        var wishlistAdded = 0
        var wishlistReplaced = 0

        payload.books.forEach { book ->
            val existing = book.isbn?.takeIf { it.isNotBlank() }?.let { bookRepo.findByIsbn(it) }
            if (existing != null) {
                booksSkipped++
            } else {
                bookRepo.upsert(book)
                booksAdded++
            }
        }

        payload.wishlist.forEach { w ->
            val existing = w.isbn?.takeIf { it.isNotBlank() }?.let { wishlistRepo.findByIsbn(it) }
            if (existing != null) {
                wishlistRepo.upsert(w.copy(id = existing.id, addedDate = existing.addedDate))
                wishlistReplaced++
            } else {
                wishlistRepo.upsert(w)
                wishlistAdded++
            }
        }

        return Summary(
            format = format,
            booksAdded = booksAdded,
            booksSkipped = booksSkipped,
            wishlistAdded = wishlistAdded,
            wishlistReplaced = wishlistReplaced,
            parseErrors = payload.parseErrors,
        )
    }
}
