package dev.khoj.pitaka.data.publish

import dev.khoj.pitaka.domain.model.Book

/**
 * World-facing publish payload (the `books.json` uploaded to GitHub Pages).
 *
 * SEPARATE from [dev.khoj.pitaka.data.export.PitakaExport] on purpose. That
 * type's contract forbids vault-derived fields on its `Book` shape ("if you
 * ever add fields derived from the vault, do it in a *separate* payload type,
 * never here"). Coarse availability (Q1=C) IS vault-derived, so it lives here,
 * on [PublishBook], not on the canonical re-importable `Book`.
 *
 * D4 posture: every [PublishBook] is built from a redacted Book (id/notes/
 * location/source/addedDate/needsMetadata already stripped by
 * `redactForPublish`). The only vault-derived field is [PublishBook.availability],
 * a COARSE status string — never an exact loaned/available count (Q1=C).
 */
data class PublishExport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<PublishBook>,
) {
    companion object {
        /**
         * Kept in lockstep with the viewer's `data.schemaVersion > N` guard in
         * `assets/publish/index.html`. The viewer tolerates additive fields, so
         * adding `genre`/`language`/`availability` did NOT need a bump — they are
         * all optional reads. Still 1.
         */
        const val SCHEMA_VERSION: Int = 1
    }
}

/**
 * One book in the world-facing payload. Carries only viewer-facing catalog
 * metadata plus the coarse availability flag. No id, notes, location, source,
 * addedDate — those never leave the device (see `redactForPublish`).
 */
data class PublishBook(
    val title: String,
    val titleTransliteration: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val genre: String? = null,
    val language: String? = null,
    val coverUrl: String? = null,
    /**
     * Reader age band as the [dev.khoj.pitaka.domain.model.Book.AgeGroup] token
     * (above-3 / above-6 / above-10 / above-15 / advanced), or null. Public
     * catalog info (NOT stripped). The viewer maps the token to a label + uses
     * it for the age-group sort. Stable token (not ordinal) so the JSON is
     * self-describing and survives enum edits.
     */
    val ageGroup: String? = null,
    /**
     * Coarse availability status (Q1=C). One of [AVAILABLE] / [OUT], or null
     * when it could not be computed (vault locked at publish time, Q2=A — the
     * field is simply omitted and the viewer shows no status). NEVER an exact
     * count: "All out" reveals that every copy is lent, which is the maximum
     * the user consented to leak; per-copy numbers stay on-device.
     */
    val availability: String? = null,
) {
    companion object {
        const val AVAILABLE = "available"
        const val OUT = "out"

        /**
         * Build a [PublishBook] from an already-redacted [Book] plus an optional
         * coarse availability flag. The [Book] MUST have been through
         * `redactForPublish` first (this function does not re-strip).
         */
        fun fromRedacted(book: Book, availability: String?): PublishBook = PublishBook(
            title = book.title,
            titleTransliteration = book.titleTransliteration,
            author = book.author,
            isbn = book.isbn,
            publisher = book.publisher,
            publishedYear = book.publishedYear,
            genre = book.genre,
            language = book.language,
            coverUrl = book.coverUrl,
            ageGroup = book.ageGroup?.token,
            availability = availability,
        )
    }
}
