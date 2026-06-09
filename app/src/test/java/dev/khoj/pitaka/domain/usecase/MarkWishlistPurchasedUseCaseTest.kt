package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.WishlistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MarkWishlistPurchasedUseCaseTest {

    private val sampleWishlist = WishlistBook(
        id = 1L,
        title = "Godaan",
        author = "Premchand",
        isbn = "9788121615568",
        priority = WishlistBook.PRIORITY_HIGH,
        addedDate = 0L,
    )

    @Test
    fun missing_book_returns_NotFound() = runBlocking {
        val wishlist = mockk<WishlistRepository>(relaxed = true)
        coEvery { wishlist.getById(1L) } returns null
        val books = mockk<BookRepository>(relaxed = true)

        val sut = MarkWishlistPurchasedUseCase(wishlist, books)
        val r = sut(wishlistBookId = 1L, moveToLibrary = false)
        assertThat(r).isEqualTo(MarkWishlistPurchasedUseCase.Result.NotFound)
    }

    @Test
    fun marks_purchased_without_moving_to_library() = runBlocking {
        val wishlist = mockk<WishlistRepository>(relaxed = true)
        val books = mockk<BookRepository>(relaxed = true)
        val slot = slot<WishlistBook>()
        coEvery { wishlist.getById(1L) } returns sampleWishlist
        coEvery { wishlist.upsert(capture(slot)) } returns 1L

        val sut = MarkWishlistPurchasedUseCase(wishlist, books)
        val r = sut(wishlistBookId = 1L, moveToLibrary = false, clock = { 999L })
        assertThat(r).isEqualTo(MarkWishlistPurchasedUseCase.Result.Success)
        assertThat(slot.captured.purchased).isTrue()
        assertThat(slot.captured.purchasedDate).isEqualTo(999L)
        coVerify(exactly = 0) { books.upsert(any()) }
    }

    @Test
    fun moves_to_library_when_ISBN_is_new() = runBlocking {
        val wishlist = mockk<WishlistRepository>(relaxed = true)
        val books = mockk<BookRepository>(relaxed = true)
        coEvery { wishlist.getById(1L) } returns sampleWishlist
        coEvery { books.findByIsbn(sampleWishlist.isbn!!) } returns null
        val bookSlot = slot<Book>()
        coEvery { books.upsert(capture(bookSlot)) } returns 42L

        val sut = MarkWishlistPurchasedUseCase(wishlist, books)
        val r = sut(wishlistBookId = 1L, moveToLibrary = true, clock = { 999L })
        assertThat(r).isEqualTo(MarkWishlistPurchasedUseCase.Result.Success)
        assertThat(bookSlot.captured.title).isEqualTo("Godaan")
        assertThat(bookSlot.captured.addedDate).isEqualTo(999L)
        assertThat(bookSlot.captured.copyCount).isEqualTo(1)
    }

    @Test
    fun refuses_to_move_when_ISBN_already_in_library() = runBlocking {
        val wishlist = mockk<WishlistRepository>(relaxed = true)
        val books = mockk<BookRepository>(relaxed = true)
        coEvery { wishlist.getById(1L) } returns sampleWishlist
        coEvery { books.findByIsbn(sampleWishlist.isbn!!) } returns Book(
            id = 7L, title = "Godaan (existing)", addedDate = 0L
        )

        val sut = MarkWishlistPurchasedUseCase(wishlist, books)
        val r = sut(wishlistBookId = 1L, moveToLibrary = true, clock = { 999L })
        assertThat(r).isInstanceOf(MarkWishlistPurchasedUseCase.Result.AlreadyInLibrary::class.java)
        val already = r as MarkWishlistPurchasedUseCase.Result.AlreadyInLibrary
        assertThat(already.existingBookId).isEqualTo(7L)
        coVerify(exactly = 0) { books.upsert(any()) }
    }
}
