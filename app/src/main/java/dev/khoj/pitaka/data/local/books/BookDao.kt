package dev.khoj.pitaka.data.local.books

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // --- Recently-added newest first (D22 default sort) ---
    @Query("SELECT * FROM books ORDER BY added_date DESC, id DESC")
    fun observeAllByRecentlyAdded(): Flow<List<BookEntity>>

    // Language A→Z, blanks last; title_sort as a stable tiebreak. (Rev R sort.)
    @Query(
        """
        SELECT * FROM books
        ORDER BY
            CASE WHEN language IS NULL OR TRIM(language) = '' THEN 1 ELSE 0 END,
            language COLLATE NOCASE ASC,
            title_sort ASC,
            id ASC
        """
    )
    fun observeAllByLanguage(): Flow<List<BookEntity>>

    // Age band by sortRank (above-3 → above-6 → above-10 → above-15 → advanced),
    // nulls last. age_group is now a TEXT token, so we map each token to its
    // band rank with a CASE rather than ordering by the token's alphabetical
    // value (which would mis-order, e.g. "above-10" before "above-3"). Mirrors
    // Book.AgeGroup.sortRank; keep the two in sync. (Rev R sort.)
    @Query(
        """
        SELECT * FROM books
        ORDER BY
            CASE WHEN age_group IS NULL THEN 1 ELSE 0 END,
            CASE age_group
                WHEN 'above-3'  THEN 0
                WHEN 'above-6'  THEN 1
                WHEN 'above-10' THEN 2
                WHEN 'above-15' THEN 3
                WHEN 'advanced' THEN 4
                ELSE 99
            END ASC,
            title_sort ASC,
            id ASC
        """
    )
    fun observeAllByAgeGroup(): Flow<List<BookEntity>>

    // --- Search (D16 + Rev R: title / transliteration / author / isbn / location / genre, substring, case-insensitive) ---
    @Query(
        """
        SELECT * FROM books
        WHERE LOWER(title) LIKE '%' || LOWER(:q) || '%'
           OR (title_transliteration IS NOT NULL AND LOWER(title_transliteration) LIKE '%' || LOWER(:q) || '%')
           OR (author IS NOT NULL AND LOWER(author) LIKE '%' || LOWER(:q) || '%')
           OR (isbn   IS NOT NULL AND LOWER(isbn)   LIKE '%' || LOWER(:q) || '%')
           OR (location IS NOT NULL AND LOWER(location) LIKE '%' || LOWER(:q) || '%')
           OR (genre IS NOT NULL AND LOWER(genre) LIKE '%' || LOWER(:q) || '%')
        ORDER BY added_date DESC, id DESC
        """
    )
    fun searchByRecentlyAdded(q: String): Flow<List<BookEntity>>

    // FTS fast path (P1). Joins the FTS index to books by rowid=id and orders
    // newest-first to match the LIKE path. Caller passes an FTS MATCH expression
    // (e.g. "term1* term2*"). Token/prefix matching — the repository falls back
    // to searchByRecentlyAdded for substring/numeric queries that FTS misses.
    @Query(
        """
        SELECT b.* FROM books AS b
        JOIN books_fts AS f ON b.id = f.rowid
        WHERE books_fts MATCH :match
        ORDER BY b.added_date DESC, b.id DESC
        """
    )
    fun searchFts(match: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    // One-shot snapshot of every row (no Flow). Used by the cover-heal
    // migration, which must read a consistent list exactly once — a Flow's
    // first emission can race concurrent writes during app startup.
    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun getAll(): List<BookEntity>

    // Single-book reactive observe (Lend screen needs exactly one known book,
    // not the whole library).
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    // Resolve a specific set of books by id (Borrower profile needs only the
    // books referenced by that borrower's loans, not the entire library).
    @Query("SELECT * FROM books WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<BookEntity>>

    // Stale-metadata books (D11 skeletons) for the Pending screen. A partial
    // index on needs_metadata makes this a small index scan instead of a full
    // table read + Kotlin filter — matters once the library is large.
    @Query("SELECT * FROM books WHERE needs_metadata = 1 ORDER BY added_date DESC, id DESC")
    fun observeNeedsMetadata(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): BookEntity?

    // Resolve a book by its stable cross-device uid (PLAN-merge.md merge key).
    @Query("SELECT * FROM books WHERE book_uid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): BookEntity?

    // Soft-delete: mark a book removed (PLAN-merge.md). Never hard-deletes.
    @Query("UPDATE books SET removed = 1, removed_at = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    // Restore a soft-deleted book (clears the flag + timestamp).
    @Query("UPDATE books SET removed = 0, removed_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Upsert
    suspend fun upsert(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    // Bulk insert for the debug-only seed tool (generate N test books). Single
    // transaction so seeding thousands of rows doesn't thrash the DB.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(books: List<BookEntity>)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Surgical delete of debug-seeded rows. Matches BOTH the current sentinel
    // prefix (SEEDTEST-...) and the legacy numeric block (9781000000000..
    // 9781000009999) used by an earlier seed build. The legacy block is an
    // unmistakably synthetic ISBN range; real ISBNs won't fall in it.
    // Returns the number of rows deleted.
    @Query(
        """
        DELETE FROM books
        WHERE isbn LIKE :sentinelPattern
           OR isbn LIKE :legacyPattern
        """
    )
    suspend fun deleteSeedBooks(sentinelPattern: String, legacyPattern: String): Int

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    // Distinct facet values for the language filter chips. Non-blank only,
    // ordered for a stable chip row. Low-cardinality column; a DISTINCT scan is
    // cheap. (Rev R: genre is no longer a facet — it's search-only.)
    @Query("SELECT DISTINCT language FROM books WHERE language IS NOT NULL AND TRIM(language) != '' ORDER BY language COLLATE NOCASE ASC")
    fun observeDistinctLanguages(): Flow<List<String>>
}
