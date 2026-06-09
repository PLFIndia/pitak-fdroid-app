package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.BookMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LookupIsbnUseCaseTest {

    @Test
    fun normalize_strips_dashes_and_spaces_and_uppercases() {
        assertThat(LookupIsbnUseCase.normalize(" 978-0-14-042844-5 ")).isEqualTo("9780140428445")
        assertThat(LookupIsbnUseCase.normalize("0-306-40615-X")).isEqualTo("030640615X")
    }

    @Test
    fun blank_input_short_circuits_without_calling_service() = runBlocking {
        val service = mockk<IsbnLookupService>(relaxed = true)
        val sut = LookupIsbnUseCase(service)
        val r = sut("   ")
        assertThat(r).isEqualTo(LookupResult.NotFound)
        coVerify(exactly = 0) { service.lookupByIsbn(any()) }
    }

    @Test
    fun normalized_input_is_forwarded_to_service() = runBlocking {
        val service = mockk<IsbnLookupService>()
        coEvery { service.lookupByIsbn("9780140428445") } returns
            LookupResult.Found(BookMetadata(isbn = "9780140428445", title = "ok"))
        val sut = LookupIsbnUseCase(service)
        val r = sut("978-0-14-042844-5")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        coVerify { service.lookupByIsbn("9780140428445") }
    }
}

class SearchByTitleUseCaseTest {
    @Test
    fun blank_returns_Empty_without_calling_service() = runBlocking {
        val service = mockk<IsbnLookupService>(relaxed = true)
        val sut = SearchByTitleUseCase(service)
        val r = sut("   ")
        assertThat(r).isEqualTo(SearchResult.Empty)
        coVerify(exactly = 0) { service.searchByTitle(any(), any()) }
    }

    @Test
    fun forwards_trimmed_query() = runBlocking {
        val service = mockk<IsbnLookupService>()
        coEvery { service.searchByTitle("godaan", 20) } returns SearchResult.Empty
        val sut = SearchByTitleUseCase(service)
        sut("  godaan  ")
        coVerify { service.searchByTitle("godaan", 20) }
    }
}
