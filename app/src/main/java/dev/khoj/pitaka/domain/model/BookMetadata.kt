package dev.khoj.pitaka.domain.model

/**
 * Metadata returned by an ISBN lookup service. Mirrors the subset of the
 * [Book] domain model that public ISBN APIs reliably populate.
 *
 * All fields except `isbn` are optional — Open Library and Google Books both
 * return wildly inconsistent records and the §1.1 graceful-degradation rule
 * means we accept what's there and ignore what isn't.
 */
data class BookMetadata(
    val isbn: String,
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val genre: String? = null,
    val language: String? = null,
)
