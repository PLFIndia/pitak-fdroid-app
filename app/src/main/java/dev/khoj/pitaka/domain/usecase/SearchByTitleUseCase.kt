package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.SearchResult
import javax.inject.Inject

/**
 * Search both ISBN providers by title. D7 fallback path — shown when an ISBN
 * lookup misses or the user types a title with no ISBN to scan.
 *
 * The composed [IsbnLookupService] (ChainedIsbnLookup in practice) does the
 * provider routing and dedup; this use case is a thin wrapper that hides the
 * indirection from the ViewModel.
 */
class SearchByTitleUseCase @Inject constructor(
    private val service: IsbnLookupService,
) {
    suspend operator fun invoke(query: String, limit: Int = 20): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResult.Empty
        return service.searchByTitle(trimmed, limit)
    }
}
