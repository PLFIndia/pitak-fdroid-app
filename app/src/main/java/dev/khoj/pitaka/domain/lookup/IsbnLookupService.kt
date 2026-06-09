package dev.khoj.pitaka.domain.lookup

import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.TitleSearchResult

/**
 * Contract for an ISBN lookup provider.
 *
 * Implementations live in `data/remote/` (Open Library, Google Books) and
 * a composer in `data/remote/ChainedIsbnLookup` runs them in fallback order
 * with a cache layer.
 *
 * Per §1.1: every method must degrade gracefully. Network failure returns
 * [LookupResult.NetworkError]; an upstream-shape mismatch returns
 * [LookupResult.NotFound] — never a crash, never a `?` propagation, never a
 * silent empty record.
 */
interface IsbnLookupService {
    suspend fun lookupByIsbn(isbn: String): LookupResult
    suspend fun searchByTitle(query: String, limit: Int = 20): SearchResult
}

sealed interface LookupResult {
    data class Found(val metadata: BookMetadata) : LookupResult
    data object NotFound : LookupResult
    data class NetworkError(val cause: Throwable?) : LookupResult
}

sealed interface SearchResult {
    data class Found(val results: List<TitleSearchResult>) : SearchResult
    data object Empty : SearchResult
    data class NetworkError(val cause: Throwable?) : SearchResult
}
