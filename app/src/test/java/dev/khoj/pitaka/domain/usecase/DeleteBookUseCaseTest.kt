package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.data.vault.VaultSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DeleteBookUseCaseTest {

    @Test
    fun returns_Success_and_purges_loans_when_vault_unlocked() = runBlocking {
        val books = mockk<BookRepository>(relaxed = true)
        val loans = mockk<LoanRepository>(relaxed = true)
        val session = mockk<VaultSession>()
        every { session.isUnlocked() } returns true
        coEvery { loans.deleteAllForBook(any()) } returns 3

        val sut = DeleteBookUseCase(books, loans, session)
        val r = sut(id = 5L)
        assertThat(r).isEqualTo(DeleteBookUseCase.Result.Success)
        coVerify { loans.deleteAllForBook(5L) }
        coVerify { books.delete(5L) }
    }

    @Test
    fun returns_RequiresVaultUnlock_when_vault_locked() = runBlocking {
        val books = mockk<BookRepository>(relaxed = true)
        val loans = mockk<LoanRepository>(relaxed = true)
        val session = mockk<VaultSession>()
        every { session.isUnlocked() } returns false

        val sut = DeleteBookUseCase(books, loans, session)
        val r = sut(id = 5L)
        assertThat(r).isEqualTo(DeleteBookUseCase.Result.RequiresVaultUnlock)
        coVerify(exactly = 0) { books.delete(any()) }
        coVerify(exactly = 0) { loans.deleteAllForBook(any()) }
    }
}
