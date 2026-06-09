package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Inject

/**
 * Mark a wishlist book purchased; optionally promote it into the Library
 * with a fresh `addedDate` and `copyCount = 1`.
 *
 * D2 interaction: if `moveToLibrary` is true *and* the ISBN already exists
 * in the Library, the call returns [Result.AlreadyInLibrary] with the
 * existing book's id; the screen surfaces the same Open/Add-duplicate/Cancel
 * dialog and re-invokes via [BookRepository.upsert] directly.
 */
class MarkWishlistPurchasedUseCase @Inject constructor(
    private val wishlist: WishlistRepository,
    private val books: BookRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data class AlreadyInLibrary(val existingBookId: Long) : Result
    }

    suspend operator fun invoke(
        wishlistBookId: Long,
        moveToLibrary: Boolean,
        clock: () -> Long = System::currentTimeMillis,
    ): Result {
        val existing = wishlist.getById(wishlistBookId) ?: return Result.NotFound
        val now = clock()
        // Mark wishlist row as purchased regardless of move flag.
        val updated = existing.copy(purchased = true, purchasedDate = now)
        wishlist.upsert(updated)

        if (!moveToLibrary) return Result.Success

        // Duplicate-ISBN check on the library side (D2).
        val isbn = existing.isbn?.takeIf { it.isNotBlank() }
        if (isbn != null) {
            val alreadyInLibrary = books.findByIsbn(isbn)
            if (alreadyInLibrary != null) {
                return Result.AlreadyInLibrary(existingBookId = alreadyInLibrary.id)
            }
        }
        books.upsert(existing.toLibraryBook(now))
        return Result.Success
    }
}

private fun WishlistBook.toLibraryBook(now: Long): Book = Book(
    title = title,
    titleTransliteration = titleTransliteration,
    author = author,
    isbn = isbn,
    publisher = publisher,
    publishedYear = publishedYear,
    coverUrl = coverUrl,
    notes = notes,
    addedDate = now,
    copyCount = 1,
    needsMetadata = needsMetadata,
)
