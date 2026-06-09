package dev.khoj.pitaka.data.local.books

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * Full-text search shadow of [BookEntity] (P1 — fast search for heavy libraries).
 *
 * `contentEntity = BookEntity::class` makes this an **external-content** FTS4
 * table: the text isn't stored twice — FTS keeps only its inverted index and
 * reads content from `books`. Room auto-generates the INSERT/UPDATE/DELETE
 * triggers that keep the index in sync with `books`, so we never hand-maintain
 * the mirror (single source of truth: the `books` table).
 *
 * Columns mirror the D16 search surface: title, transliteration, author, isbn,
 * location, genre. `rowid` aliases `books.id`, so a MATCH result's rowid is the
 * book id. (genre added in v7 — it became search-only when the genre filter
 * chip was dropped.)
 *
 * NOTE on semantics: FTS matches whole tokens / prefixes (`token*`), not
 * arbitrary substrings. The repository runs this fast path first and falls back
 * to the substring LIKE scan when FTS finds nothing (or for short/numeric
 * queries like a partial ISBN) — preserving today's substring behaviour,
 * especially for mid-word Devanagari/Gurmukhi (D8) and middle-of-ISBN matches.
 */
@Fts4(contentEntity = BookEntity::class)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "title_transliteration")
    val titleTransliteration: String?,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "isbn")
    val isbn: String?,

    @ColumnInfo(name = "location")
    val location: String?,

    @ColumnInfo(name = "genre")
    val genre: String?,
)
