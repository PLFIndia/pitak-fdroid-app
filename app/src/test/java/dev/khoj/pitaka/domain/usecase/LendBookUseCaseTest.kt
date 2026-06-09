package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LendBookUseCaseTest {

    @Test
    fun fails_without_book() = runBlocking {
        val borrowers = mockk<BorrowerRepository>(relaxed = true)
        val loans = mockk<LoanRepository>(relaxed = true)
        val sut = LendBookUseCase(borrowers, loans)
        val r = sut(bookId = 0L, existingBorrowerId = 1L)
        assertThat(r).isEqualTo(LendBookUseCase.Result.BookRequired)
    }

    @Test
    fun fails_without_borrower() = runBlocking {
        val borrowers = mockk<BorrowerRepository>(relaxed = true)
        val loans = mockk<LoanRepository>(relaxed = true)
        val sut = LendBookUseCase(borrowers, loans)
        val r = sut(bookId = 5L, existingBorrowerId = null, newBorrowerName = null)
        assertThat(r).isEqualTo(LendBookUseCase.Result.BorrowerRequired)
    }

    @Test
    fun creates_new_borrower_and_loan_when_name_provided() = runBlocking {
        val borrowers = mockk<BorrowerRepository>()
        val loans = mockk<LoanRepository>()
        coEvery { borrowers.findByName("Ravi") } returns null
        val borrowerSlot = slot<Borrower>()
        coEvery { borrowers.upsert(capture(borrowerSlot)) } returns 42L
        coEvery { loans.upsert(any()) } returns 7L

        val sut = LendBookUseCase(borrowers, loans)
        val r = sut(bookId = 5L, newBorrowerName = "Ravi", newBorrowerContact = " ravi@example.com ")

        assertThat(r).isInstanceOf(LendBookUseCase.Result.Success::class.java)
        val success = r as LendBookUseCase.Result.Success
        assertThat(success.borrowerId).isEqualTo(42L)
        assertThat(success.loanId).isEqualTo(7L)
        assertThat(borrowerSlot.captured.name).isEqualTo("Ravi")
        assertThat(borrowerSlot.captured.contact).isEqualTo("ravi@example.com")
    }

    @Test
    fun reuses_existing_borrower_when_name_matches() = runBlocking {
        val borrowers = mockk<BorrowerRepository>(relaxed = true)
        val loans = mockk<LoanRepository>(relaxed = true)
        coEvery { borrowers.findByName("Ravi") } returns Borrower(id = 11L, name = "Ravi")
        coEvery { loans.upsert(any()) } returns 7L

        val sut = LendBookUseCase(borrowers, loans)
        val r = sut(bookId = 5L, newBorrowerName = "Ravi")

        assertThat((r as LendBookUseCase.Result.Success).borrowerId).isEqualTo(11L)
        coVerify(exactly = 0) { borrowers.upsert(any()) }
    }

    @Test
    fun prefers_existing_borrower_id_when_present() = runBlocking {
        val borrowers = mockk<BorrowerRepository>(relaxed = true)
        val loans = mockk<LoanRepository>()
        coEvery { loans.upsert(any()) } returns 7L

        val sut = LendBookUseCase(borrowers, loans)
        val r = sut(bookId = 5L, existingBorrowerId = 99L, newBorrowerName = "ignored")

        assertThat((r as LendBookUseCase.Result.Success).borrowerId).isEqualTo(99L)
        coVerify(exactly = 0) { borrowers.findByName(any()) }
        coVerify(exactly = 0) { borrowers.upsert(any()) }
    }
}
