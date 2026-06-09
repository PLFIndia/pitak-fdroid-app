package dev.khoj.pitaka.domain.repository

import dev.khoj.pitaka.domain.model.Book
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the unencrypted books store (pitaka.md §4.3).
 *
 * Phase 1 surface:
 * - observe all (for the Library list, sorted recently-added newest first per D22)
 * - get by id
 * - find by ISBN (used by the future duplicate-ISBN dialog from D2; in Phase 1
 *   only the manual-entry form calls it, to surface the dialog when a user
 *   types in an ISBN they already own)
 * - upsert (insert or update by id; returns the row id)
 * - delete by id
 * - basic text search across title / transliteration / author / isbn (D16),
 *   substring case-insensitive
 */
interface BookRepository {

    fun observeAll(
        sort: BookSort = BookSort.RecentlyAdded,
        filter: LibraryFilter = LibraryFilter.None,
    ): Flow<List<Book>>

    /** Distinct non-blank languages present in the library, for the filter chips. */
    fun observeLanguages(): Flow<List<String>>

    /** Books still flagged as skeleton/needs-metadata (D11), for the Pending screen. */
    fun observeNeedsMetadata(): Flow<List<Book>>

    /** Reactive single-book lookup (Lend screen). */
    fun observeById(id: Long): Flow<Book?>

    /** Resolve a specific set of books by id (Borrower profile loan rows). */
    fun observeByIds(ids: List<Long>): Flow<List<Book>>

    fun search(
        query: String,
        sort: BookSort = BookSort.RecentlyAdded,
        filter: LibraryFilter = LibraryFilter.None,
    ): Flow<List<Book>>

    suspend fun getById(id: Long): Book?

    /**
     * One-shot snapshot of the whole library (no Flow). For batch/migration
     * use that must read a consistent list exactly once; a Flow's first
     * emission can race concurrent writes during startup.
     */
    suspend fun getAll(): List<Book>

    suspend fun findByIsbn(isbn: String): Book?

    /** Resolve a book by its stable cross-device uid (merge key, PLAN-merge.md). */
    suspend fun findByUid(uid: String): Book?

    suspend fun upsert(book: Book): Long

    suspend fun delete(id: Long)

    /**
     * Soft-delete (PLAN-merge.md): mark the book "removed from library" instead
     * of hard-deleting. It stays in the DB (visible with a badge, actions
     * blocked, stripped from publish) and merges like any other field.
     */
    suspend fun markRemoved(id: Long, at: Long = System.currentTimeMillis())

    /** Restore a soft-deleted book (clears the removed flag). */
    suspend fun restore(id: Long)

    /**
     * DEBUG-ONLY seed helper: bulk-insert [count] synthetic books to measure
     * heavy-library performance. Never call from a release build (the only
     * caller is gated behind BuildConfig.DEBUG).
     */
    suspend fun seedRandomBooks(count: Int)

    /**
     * DEBUG-ONLY: delete every book created by [seedRandomBooks] (matched by the
     * sentinel ISBN prefix), leaving all real books untouched. Returns the count
     * removed. Caller gated behind BuildConfig.DEBUG.
     */
    suspend fun deleteSeedBooks(): Int
}

/**
 * Library sort orders. Persisted per screen via AppPreferences.
 *
 * Revision R: Title/Author sorts were CUT (those are search axes now). The sort
 * dropdown is {Date added, Language, Age group}.
 *  - RecentlyAdded: added_date DESC (D22 default).
 *  - LanguageAsc: language A→Z, blanks last.
 *  - AgeGroupAsc: age band by sortRank (above-3 → above-6 → above-10 → above-15 → advanced), nulls last.
 */
enum class BookSort {
    RecentlyAdded,
    LanguageAsc,
    AgeGroupAsc,
}

/**
 * Categorical facet filter for the Library list. Revision R: GENRE was removed
 * (genre is search-only now), leaving LANGUAGE as the sole facet. Orthogonal to
 * [BookSort]: a filter narrows the set, a sort orders it. null = no filtering
 * ([None]). Matching is exact on the stored value (case-insensitive trim handled
 * in the repository). Combinable with any sort and with search.
 */
data class LibraryFilter(
    val language: String? = null,
) {
    val isActive: Boolean get() = language != null

    companion object {
        val None = LibraryFilter()
    }
}
