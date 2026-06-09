package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AddBookUseCaseTest {

    private fun prefs(maintainer: String = ""): AppPreferences =
        mockk<AppPreferences>(relaxed = true).also {
            every { it.maintainerName() } returns flowOf(maintainer)
        }

    @Test
    fun blank_title_returns_TitleRequired() = runBlocking {
        val repo = mockk<BookRepository>(relaxed = true)
        val sut = AddBookUseCase(repo, prefs())
        val result = sut(Book(title = "   "))
        assertThat(result).isEqualTo(AddBookUseCase.Result.TitleRequired)
    }

    @Test
    fun valid_book_persists_and_returns_id() = runBlocking {
        val repo = mockk<BookRepository>()
        coEvery { repo.upsert(any()) } returns 42L
        val sut = AddBookUseCase(repo, prefs())
        val result = sut(Book(title = "Godaan"))
        assertThat(result).isInstanceOf(AddBookUseCase.Result.Success::class.java)
        assertThat((result as AddBookUseCase.Result.Success).id).isEqualTo(42L)
        coVerify(exactly = 1) { repo.upsert(any()) }
    }

    @Test
    fun new_book_is_stamped_with_maintainer_name() = runBlocking {
        val repo = mockk<BookRepository>()
        val slot = mutableListOf<Book>()
        coEvery { repo.upsert(capture(slot)) } returns 1L
        val sut = AddBookUseCase(repo, prefs(maintainer = "Asha"))
        sut(Book(title = "Godaan"))
        assertThat(slot.single().addedBy).isEqualTo("Asha")
    }

    @Test
    fun blank_maintainer_leaves_addedBy_null() = runBlocking {
        val repo = mockk<BookRepository>()
        val slot = mutableListOf<Book>()
        coEvery { repo.upsert(capture(slot)) } returns 1L
        val sut = AddBookUseCase(repo, prefs(maintainer = ""))
        sut(Book(title = "Godaan"))
        assertThat(slot.single().addedBy).isNull()
    }
}
