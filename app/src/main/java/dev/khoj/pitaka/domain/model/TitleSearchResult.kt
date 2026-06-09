package dev.khoj.pitaka.domain.model

/**
 * One result row from a title-based search (D7 fallback path).
 *
 * Returned by [dev.khoj.pitaka.domain.lookup.IsbnLookupService.searchByTitle]
 * when the user couldn't find a book via direct ISBN scan and wants to look
 * it up by title instead.
 *
 * `bookId` is the upstream provider's stable identifier (Open Library `key`
 * or Google Books `id`); not used by Pitaka domain logic — only there in case
 * a future "show more details" affordance wants to fetch the full record.
 */
data class TitleSearchResult(
    val sourceKey: String,
    val title: String,
    val author: String? = null,
    val publishedYear: Int? = null,
    val isbn: String? = null,
    val coverUrl: String? = null,
)
