package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.domain.repository.LoanRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ReturnLoanUseCaseTest {

    @Test
    fun missing_loan_returns_NotFound() = runBlocking {
        val loans = mockk<LoanRepository>()
        coEvery { loans.getById(1L) } returns null
        val sut = ReturnLoanUseCase(loans)
        val r = sut(loanId = 1L)
        assertThat(r).isEqualTo(ReturnLoanUseCase.Result.NotFound)
    }

    @Test
    fun sets_returnedDate_to_now() = runBlocking {
        val loans = mockk<LoanRepository>()
        coEvery { loans.getById(1L) } returns Loan(
            id = 1L, bookId = 5L, borrowerId = 7L, lentDate = 0L
        )
        val slot = slot<Loan>()
        coEvery { loans.upsert(capture(slot)) } returns 1L
        val sut = ReturnLoanUseCase(loans)
        val r = sut(loanId = 1L, returnedAt = 9_999L)
        assertThat(r).isEqualTo(ReturnLoanUseCase.Result.Success)
        assertThat(slot.captured.returnedDate).isEqualTo(9_999L)
    }
}
