package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateBookUseCaseTest {

    @Test
    fun blank_title_returns_TitleRequired() = runBlocking {
        val repo = mockk<BookRepository>(relaxed = true)
        val sut = UpdateBookUseCase(repo)
        val r = sut(Book(id = 1L, title = "", addedDate = 0L))
        assertThat(r).isEqualTo(UpdateBookUseCase.Result.TitleRequired)
    }

    @Test
    fun missing_book_returns_NotFound() = runBlocking {
        val repo = mockk<BookRepository>()
        coEvery { repo.getById(7L) } returns null
        val sut = UpdateBookUseCase(repo)
        val r = sut(Book(id = 7L, title = "ok", addedDate = 0L))
        assertThat(r).isEqualTo(UpdateBookUseCase.Result.NotFound)
    }

    @Test
    fun changing_addedDate_now_persists() = runBlocking {
        // D30 amended: addedDate ("Date added") is user-editable. A changed
        // date must be accepted and persisted, not rejected.
        val repo = mockk<BookRepository>()
        coEvery { repo.getById(1L) } returns Book(id = 1L, title = "ok", addedDate = 100L)
        coEvery { repo.upsert(any()) } returns 1L
        val sut = UpdateBookUseCase(repo)
        val r = sut(Book(id = 1L, title = "ok", addedDate = 999L))
        assertThat(r).isEqualTo(UpdateBookUseCase.Result.Success)
        coVerify { repo.upsert(match { it.addedDate == 999L }) }
    }

    @Test
    fun valid_update_persists() = runBlocking {
        val repo = mockk<BookRepository>()
        val existing = Book(id = 5L, title = "old", addedDate = 100L)
        coEvery { repo.getById(5L) } returns existing
        coEvery { repo.upsert(any()) } returns 5L
        val sut = UpdateBookUseCase(repo)
        val r = sut(existing.copy(title = "new"))
        assertThat(r).isEqualTo(UpdateBookUseCase.Result.Success)
        coVerify { repo.upsert(match { it.title == "new" && it.addedDate == 100L }) }
    }
}
