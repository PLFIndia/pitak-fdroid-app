package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import javax.inject.Inject

/**
 * Look up an ISBN and return what the provider chain found.
 *
 * Per D7 the *result* — particularly NotFound or NetworkError — drives the
 * UI fallback to title-search. Per D11 there is no auto-retry; if the user
 * is offline they get NetworkError, save a skeleton, and revisit later.
 *
 * Input is normalized: trims whitespace, strips dashes/spaces (`978-0-14-…` →
 * `9780014...`), uppercases the trailing `X` check digit if present (ISBN-10).
 */
class LookupIsbnUseCase @Inject constructor(
    private val service: IsbnLookupService,
) {
    suspend operator fun invoke(isbn: String): LookupResult {
        val normalized = normalize(isbn)
        if (normalized.isEmpty()) return LookupResult.NotFound
        return service.lookupByIsbn(normalized)
    }

    companion object {
        fun normalize(raw: String): String =
            raw.trim()
                .replace("-", "")
                .replace(" ", "")
                .uppercase() // ISBN-10 check digit can be 'X'
    }
}
