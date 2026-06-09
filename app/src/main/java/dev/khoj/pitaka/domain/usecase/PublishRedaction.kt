package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.model.Book

/**
 * F-01 (audit): redact a [Book] for inclusion in the world-facing
 * `books.json`.
 *
 * Pure function; lives in `domain/usecase` and is unit-tested without
 * Android dependencies.
 *
 * Fields stripped:
 *  - `id` → 0L. Internal counter; enumeration leak.
 *  - `notes` → null. Often borrower-linked PII (see audit F-05).
 *  - `location` → null. Physical-shelf position; targeted-theft enabler.
 *    The model doc says this must be stripped; the previous `strip()`
 *    impl failed to honour it.
 *  - `sourceType` / `sourceDetail` → null. Private provenance ("Gift from
 *    Ravi", where it was bought). Same privacy class as `location`; the
 *    world doesn't need to know how I acquired my copy.
 *  - `addedDate` → 0L. Temporal fingerprint of purchase patterns.
 *  - `addedBy` → null. Maintainer attribution (D41) is internal coordination
 *    info among the library's maintainers, not public-catalogue data — a public
 *    page should not name who shelved each book. Same privacy class as location.
 *  - `needsMetadata` → false. Internal UI flag with no public meaning.
 *
 * Fields preserved (the bundled viewer at `assets/publish/index.html`
 * reads exactly these):
 *   title, titleTransliteration, author, publisher, publishedYear,
 *   isbn, coverUrl.
 *
 * Other standard catalog metadata kept for forward-compatible viewer
 * iterations: pageCount, language, genre, copyCount.
 *
 * Cover URL policy is delegated entirely to [resolveCoverUrl]. It is
 * invoked with the book (id intact) and must return either the value to
 * publish as `coverUrl` or null to drop the cover (the viewer's onerror
 * handler then falls back to a placeholder).
 *
 * The redactor does NOT pass the original [Book.coverUrl] through on a
 * null result — F-09 (audit): an unfiltered remote URL is exactly the
 * exfiltration vector we're closing. Callers decide what is safe; this
 * function only zeroes PII.
 *
 * The id is zeroed in the returned [Book] AFTER [resolveCoverUrl] has
 * been called, so callbacks can hash or look up the real id.
 */
fun redactForPublish(
    book: Book,
    resolveCoverUrl: (Book) -> String?,
): Book {
    val rewrittenCoverUrl: String? = resolveCoverUrl(book)
    return book.copy(
        id = 0L,
        notes = null,
        location = null,
        sourceType = null,
        sourceDetail = null,
        addedDate = 0L,
        addedBy = null,
        needsMetadata = false,
        coverUrl = rewrittenCoverUrl,
    )
}
