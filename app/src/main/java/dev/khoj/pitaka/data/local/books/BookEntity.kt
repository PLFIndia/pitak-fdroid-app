package dev.khoj.pitaka.data.local.books

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted form of [dev.khoj.pitaka.domain.model.Book].
 *
 * Indexes:
 * - `isbn` is unique nullable to enforce the D2 duplicate-ISBN rule at the DB
 *   level. Multiple null ISBNs are allowed (SQLite treats NULL as distinct).
 * - `added_date` is indexed for the recently-added sort (D22 default).
 * - `needs_metadata` is indexed so the Pending screen's stale-book query is a
 *   small index scan, not a full-table read + Kotlin filter.
 * - `title_sort` / `author_sort` are indexed lowercase shadow columns so the
 *   Title/Author sorts use an index instead of sorting the whole table in
 *   memory with LOWER() at query time. They are computed at write time with
 *   Kotlin's Unicode-aware `lowercase()` (D8 — titles may be Devanagari /
 *   Gurmukhi, which SQLite's ASCII-only LOWER()/NOCASE cannot fold). Never read
 *   by the UI; ordering keys only.
 *
 * No `status` column (D4). Lent-state lives only in the vault.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["isbn"], unique = true),
        Index(value = ["book_uid"], unique = true),
        Index(value = ["added_date"]),
        Index(value = ["needs_metadata"]),
        Index(value = ["title_sort"]),
        Index(value = ["author_sort"]),
        Index(value = ["age_group"]),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /**
     * Stable cross-device identity (UUID string). Unique. Minted at first persist
     * by the mapper (see Book.toEntity) and never changed thereafter; carried
     * verbatim through export/import so the same logical book has the same uid on
     * every device. This is the primary merge key (PLAN-merge.md). Nullable in the
     * column only for the brief pre-persist window and for legacy rows mid-migration
     * — the v7→v8 migration backfills every existing row, and the mapper always
     * supplies one on write.
     */
    @ColumnInfo(name = "book_uid")
    val bookUid: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "title_transliteration")
    val titleTransliteration: String? = null,

    @ColumnInfo(name = "author")
    val author: String? = null,

    /**
     * Lowercase shadow of [title] for indexed Title-sort. Computed at write time
     * (see Book.toEntity). Unicode-aware via Kotlin `lowercase()`. Defaults to ""
     * so legacy/test construction paths stay valid; the mapper always sets it.
     */
    @ColumnInfo(name = "title_sort", defaultValue = "")
    val titleSort: String = "",

    /** Lowercase shadow of [author] (empty when author is null) for indexed Author-sort. */
    @ColumnInfo(name = "author_sort", defaultValue = "")
    val authorSort: String = "",

    @ColumnInfo(name = "isbn")
    val isbn: String? = null,

    @ColumnInfo(name = "publisher")
    val publisher: String? = null,

    @ColumnInfo(name = "published_year")
    val publishedYear: Int? = null,

    @ColumnInfo(name = "genre")
    val genre: String? = null,

    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,

    @ColumnInfo(name = "page_count")
    val pageCount: Int? = null,

    @ColumnInfo(name = "language")
    val language: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "location")
    val location: String? = null,

    /**
     * Provenance category (how this copy was acquired). Stored as the enum
     * [dev.khoj.pitaka.domain.model.Book.SourceType] name, nullable. Not indexed
     * — it is a detail/edit field, not a search or sort axis in v1. Stripped
     * from publish (private provenance, same class as [location]).
     */
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null,

    /** Free-form bilingual provenance specifics. Nullable; not indexed. */
    @ColumnInfo(name = "source_detail")
    val sourceDetail: String? = null,

    /**
     * Reader age band as the [dev.khoj.pitaka.domain.model.Book.AgeGroup]
     * TOKEN string (e.g. "above-3"), nullable. Stored as text (not the ordinal)
     * so the persisted value is self-describing and stable across enum edits.
     * Indexed — kept for schema parity and a possible future equality age
     * filter; the Age-group SORT orders by a CASE→sortRank in the DAO, not by
     * this column's alphabetical order. Not mirrored into FTS (sort/filter axis,
     * not free text).
     */
    @ColumnInfo(name = "age_group")
    val ageGroup: String? = null,

    @ColumnInfo(name = "added_date")
    val addedDate: Long,

    @ColumnInfo(name = "copy_count", defaultValue = "1")
    val copyCount: Int = 1,

    /**
     * Set to true when the book was added with only a skeleton record (D11) —
     * e.g. scanned offline, or ISBN lookup failed and the user chose to save
     * with just the ISBN. The Library row UI surfaces a small chip; the user
     * can tap the book and use "Refresh metadata" to fill in the rest.
     */
    @ColumnInfo(name = "needs_metadata", defaultValue = "0")
    val needsMetadata: Boolean = false,

    /**
     * Soft-delete flag (PLAN-merge.md). NOT NULL default 0 (active). When true the
     * book is "removed from library": still present and visible with a badge, but
     * library actions are blocked and it is stripped from publish. Merged like any
     * other field — a difference across devices is surfaced, never auto-applied.
     * Deliberately a plain catalogue flag, NOT the D4-removed lent/lost status enum
     * (reveals nothing vault-derived).
     */
    @ColumnInfo(name = "removed", defaultValue = "0")
    val removed: Boolean = false,

    /** Epoch ms when [removed] was set; null while active. */
    @ColumnInfo(name = "removed_at")
    val removedAt: Long? = null,

    /**
     * Maintainer attribution (PLAN-merge.md D41), nullable. The handle of the
     * maintainer who first catalogued this book, stamped at creation. Self-asserted
     * (not signed); travels through export/import + survives merges. Not indexed —
     * a low-cardinality display/filter field, filtered in Kotlin like language.
     */
    @ColumnInfo(name = "added_by")
    val addedBy: String? = null,
)
