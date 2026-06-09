package dev.khoj.pitaka.data.export

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook

/**
 * Versioned export schema for the Pitaka JSON round-trip.
 *
 * §1.1 update-channel rule: users may skip versions, so the static viewer
 * (Phase 5) and a future importer must read `schemaVersion` first and
 * refuse cleanly if it's higher than they know.
 *
 * D4 invariant: this payload MUST NOT contain vault data. Library + Wishlist
 * only. Phase 5's publish path consumes the *same* shape; if you ever add
 * fields derived from the vault, do it in a *separate* payload type, never
 * here.
 *
 * Merge fields (schemaVersion 2, PLAN-merge.md): each [Book] now carries
 * `bookUid` (stable cross-device identity) and `removed`/`removedAt` (the
 * soft-delete flag). These ride along automatically because [Book] is the
 * element type — the merge importer reads them to reconcile catalogues across
 * maintainers' devices. They are plain catalogue fields, not vault-derived, so
 * the D4 invariant holds.
 *
 * Library namespace + attribution (schemaVersion 3, PLAN-merge.md D40/D41):
 * `libraryId` + `libraryName` identify WHICH library this file belongs to (the
 * merge gate requires matching IDs, else it forces a Join/Overwrite decision),
 * and each [Book] carries `addedBy` (maintainer attribution). All additive.
 */
data class PitakaExport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<Book>,
    val wishlist: List<WishlistBook>,
    /** Library namespace (D40). Empty when an older app produced the file. */
    val libraryId: String = "",
    /** Human-readable library name (D40), for legible merge warnings. */
    val libraryName: String = "",
) {
    companion object {
        /**
         * Bump only on breaking shape changes. Additive fields don't need a bump.
         *
         * v1 → v2: each book gained `bookUid` + `removed`/`removedAt` for the
         * multi-maintainer merge (PLAN-merge.md). Additive.
         * v2 → v3: added top-level `libraryId` + `libraryName` (D40) and per-book
         * `addedBy` (D41). Additive — older importers ignore the new fields; a v1/v2
         * file read by a v3 app defaults them (blank libraryId → "unknown library",
         * which the merge gate routes to the explicit Join/Overwrite decision). The
         * existing importer still refuses any file whose schemaVersion is HIGHER
         * than it knows.
         */
        const val SCHEMA_VERSION: Int = 3
    }
}
