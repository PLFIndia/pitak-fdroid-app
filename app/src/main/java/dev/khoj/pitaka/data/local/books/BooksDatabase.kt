package dev.khoj.pitaka.data.local.books

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Books database (pitaka.md §4.1.A — unencrypted).
 *
 * Tables: `books`. Tags + book_tags arrive in a later phase (D21).
 *
 * Versions:
 *   v1 (Phase 1) — initial schema.
 *   v2 (Phase 2) — adds `needs_metadata INTEGER NOT NULL DEFAULT 0` to `books`
 *                  to support the D11 skeleton-record badge.
 *   v3 (Phase 2 polish) — adds nullable `location TEXT` to `books` for the
 *                          physical-shelf field. Searchable via D16.
 *   v4 (perf/heavy-library) — adds an index on `needs_metadata` so the Pending
 *                          screen's stale-book query is an index scan instead of
 *                          a full-table read + Kotlin filter.
 *   v5 (perf/heavy-library) — adds lowercase shadow sort columns `title_sort`
 *                          and `author_sort` (+ indexes) so the Title/Author
 *                          sorts use an index instead of an in-memory LOWER()
 *                          sort of the whole table. Backfilled from existing
 *                          rows; new/edited rows get Kotlin's Unicode-aware
 *                          lowercase via the mapper.
 *   v6 (perf/heavy-library) — adds an external-content FTS4 table `books_fts`
 *                          (P1 fast search) mirroring title / transliteration /
 *                          author / isbn / location, plus Room-generated sync
 *                          triggers. The repository runs FTS prefix search first
 *                          and falls back to the substring LIKE scan.
 *   v7 (provenance + age band + genre search) — adds nullable `source_type TEXT`
 *                          (Book.SourceType enum name), `source_detail TEXT`
 *                          (free-form bilingual), and `age_group INTEGER`
 *                          (Book.AgeGroup ordinal, indexed) to `books`. Also
 *                          rebuilds the `books_fts` external-content table to add
 *                          the `genre` column (genre became search-only when the
 *                          genre filter chip was dropped). Source fields are
 *                          stripped at publish; age_group is public catalog info.
 *   v8 (multi-maintainer merge) — adds `book_uid TEXT` (UNIQUE) as the stable
 *                          cross-device identity (backfilled with a random value
 *                          per existing row), and the soft-delete pair
 *                          `removed INTEGER NOT NULL DEFAULT 0` + `removed_at
 *                          INTEGER` to `books`. No FTS change (uid/removed are not
 *                          search axes). See PLAN-merge.md.
 *   v9 (maintainer attribution) — adds nullable `added_by TEXT` to `books`
 *                          (PLAN-merge.md D41): the handle of the maintainer who
 *                          first catalogued the book, stamped at creation. Self-
 *                          asserted, travels through export/import. Not indexed
 *                          (low-cardinality display/filter field). No FTS change.
 *   v10 (age-band token migration) — changes `age_group` from an INTEGER ordinal
 *                          to a TEXT token (Book.AgeGroup.token, e.g. "above-3").
 *                          The age bands themselves changed (0-5/6-10/11-16/Advance
 *                          → above-3/above-6/above-10/above-15/advanced), so the
 *                          ordinal was both brittle and semantically stale. SQLite
 *                          cannot ALTER a column's affinity, so this is a
 *                          table-recreate: build a new `books` with `age_group
 *                          TEXT`, copy every row remapping the old ordinal to the
 *                          new token (0→above-3, 1→above-6, 2→above-10, 3→advanced,
 *                          NULL→NULL; nothing maps to above-15), drop the old table,
 *                          rename, and re-create every index. The books_fts content
 *                          triggers are dropped before the swap and recreated after
 *                          (they bind to the table by name), then the index is
 *                          rebuilt. age_group is public catalog info (not stripped).
 */
@Database(
    entities = [BookEntity::class, BookFtsEntity::class],
    version = 10,
    exportSchema = true,
)
abstract class BooksDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        const val NAME = "books.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books " +
                            "ADD COLUMN needs_metadata INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN location TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Index name must match Room's auto-generated convention so the
                // generated schema validates against the declared @Index.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_books_needs_metadata " +
                            "ON books (needs_metadata)"
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN title_sort TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN author_sort TEXT NOT NULL DEFAULT ''")
                // Backfill from existing rows. SQLite lower() is ASCII-only, but
                // Devanagari/Gurmukhi are caseless so they pass through unchanged
                // (correct); Latin folds correctly. New/edited rows are written
                // with Kotlin's Unicode-aware lowercase() via the mapper, which is
                // a superset. trim() mirrors the mapper's normalization.
                db.execSQL("UPDATE books SET title_sort = lower(trim(title))")
                db.execSQL("UPDATE books SET author_sort = lower(trim(COALESCE(author, '')))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_title_sort ON books (title_sort)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_author_sort ON books (author_sort)")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // External-content FTS4 table + Room's sync triggers. DDL copied
                // verbatim from Room's generated v6 schema so the runtime
                // identity hash matches (any drift fails Room's schema check).
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(" +
                            "`title` TEXT NOT NULL, `title_transliteration` TEXT, " +
                            "`author` TEXT, `isbn` TEXT, `location` TEXT, content=`books`)"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE " +
                            "BEFORE UPDATE ON `books` BEGIN " +
                            "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE " +
                            "BEFORE DELETE ON `books` BEGIN " +
                            "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE " +
                            "AFTER UPDATE ON `books` BEGIN " +
                            "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                            "`author`, `isbn`, `location`) VALUES " +
                            "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                            "NEW.`author`, NEW.`isbn`, NEW.`location`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_INSERT " +
                            "AFTER INSERT ON `books` BEGIN " +
                            "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                            "`author`, `isbn`, `location`) VALUES " +
                            "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                            "NEW.`author`, NEW.`isbn`, NEW.`location`); END"
                )
                // Populate the index from existing rows (the triggers only fire on
                // future writes). The 'rebuild' command re-derives the whole index
                // from the content table.
                db.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES('rebuild')")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- New plain columns on `books` ---
                // source_type / source_detail: private provenance (stripped at publish).
                // age_group: Book.AgeGroup ordinal, indexed for the age-band sort.
                db.execSQL("ALTER TABLE books ADD COLUMN source_type TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN source_detail TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN age_group INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_age_group ON books (age_group)")

                // --- Rebuild books_fts to add the `genre` column ---
                // genre became search-only (the filter chip was dropped), so it
                // must be in the FTS index. External-content FTS can't ALTER its
                // columns, so we drop the table + its sync triggers and recreate
                // them (DDL copied verbatim from the generated v7 schema), then
                // rebuild the index from the content table.
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_AFTER_INSERT")
                db.execSQL("DROP TABLE IF EXISTS books_fts")
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(" +
                            "`title` TEXT NOT NULL, `title_transliteration` TEXT, " +
                            "`author` TEXT, `isbn` TEXT, `location` TEXT, `genre` TEXT, " +
                            "content=`books`)"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE " +
                            "BEFORE UPDATE ON `books` BEGIN " +
                            "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE " +
                            "BEFORE DELETE ON `books` BEGIN " +
                            "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE " +
                            "AFTER UPDATE ON `books` BEGIN " +
                            "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                            "`author`, `isbn`, `location`, `genre`) VALUES " +
                            "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                            "NEW.`author`, NEW.`isbn`, NEW.`location`, NEW.`genre`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_INSERT " +
                            "AFTER INSERT ON `books` BEGIN " +
                            "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                            "`author`, `isbn`, `location`, `genre`) VALUES " +
                            "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                            "NEW.`author`, NEW.`isbn`, NEW.`location`, NEW.`genre`); END"
                )
                db.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES('rebuild')")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- Stable cross-device identity: book_uid (UNIQUE) ---
                // Add nullable first, backfill every existing row with a unique
                // random value, THEN add the unique index. SQLite has no UUID()
                // function and a Room migration has no Context, so we mint the
                // value in SQL: hex(randomblob(16)) is 16 random bytes rendered as
                // a 32-char hex string — collision probability is negligible and
                // it satisfies the UNIQUE index. Going forward the mapper mints a
                // proper java.util.UUID; both are just opaque unique strings.
                db.execSQL("ALTER TABLE books ADD COLUMN book_uid TEXT")
                db.execSQL("UPDATE books SET book_uid = lower(hex(randomblob(16))) WHERE book_uid IS NULL")
                // Unique index name must match Room's generated convention
                // (index_<table>_<column>) so the generated v8 schema validates.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_books_book_uid ON books (book_uid)")

                // --- Soft-delete pair (PLAN-merge.md) ---
                // removed: NOT NULL DEFAULT 0 (active) — existing rows are active.
                // removed_at: nullable epoch ms, null while active.
                db.execSQL("ALTER TABLE books ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN removed_at INTEGER")

                // No books_fts change: book_uid / removed are not search axes.
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Maintainer attribution (PLAN-merge.md D41): nullable, additive.
                // Existing rows have no attribution (added before the feature),
                // so they stay NULL — correct, not "unknown maintainer".
                db.execSQL("ALTER TABLE books ADD COLUMN added_by TEXT")
                // No books_fts change: added_by is a display/filter field, not search.
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // `age_group` changes affinity INTEGER (ordinal) → TEXT (token).
                // SQLite cannot ALTER a column's type, so recreate `books`. The
                // new-table DDL is copied verbatim from the generated v10 schema
                // (identical to v9 except `age_group` is TEXT) so Room's identity
                // hash validates post-migration.
                //
                // FTS content-sync triggers bind to `books` by name; drop them
                // before the swap and recreate them after (DDL verbatim from the
                // generated schema), then rebuild the external-content index.
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_books_fts_AFTER_INSERT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`book_uid` TEXT, `title` TEXT NOT NULL, " +
                        "`title_transliteration` TEXT, `author` TEXT, " +
                        "`title_sort` TEXT NOT NULL DEFAULT '', " +
                        "`author_sort` TEXT NOT NULL DEFAULT '', " +
                        "`isbn` TEXT, `publisher` TEXT, `published_year` INTEGER, " +
                        "`genre` TEXT, `cover_url` TEXT, `page_count` INTEGER, " +
                        "`language` TEXT, `notes` TEXT, `location` TEXT, " +
                        "`source_type` TEXT, `source_detail` TEXT, `age_group` TEXT, " +
                        "`added_date` INTEGER NOT NULL, " +
                        "`copy_count` INTEGER NOT NULL DEFAULT 1, " +
                        "`needs_metadata` INTEGER NOT NULL DEFAULT 0, " +
                        "`removed` INTEGER NOT NULL DEFAULT 0, `removed_at` INTEGER, " +
                        "`added_by` TEXT)"
                )

                // Copy every row, remapping the old ordinal to the new token.
                // B-decision mapping (user-approved): 0→above-3, 1→above-6,
                // 2→above-10, 3→advanced; NULL and anything else → NULL. Nothing
                // maps to the new "above-15" band (no old band corresponds).
                db.execSQL(
                    "INSERT INTO `books_new` (" +
                        "id, book_uid, title, title_transliteration, author, " +
                        "title_sort, author_sort, isbn, publisher, published_year, " +
                        "genre, cover_url, page_count, language, notes, location, " +
                        "source_type, source_detail, age_group, added_date, " +
                        "copy_count, needs_metadata, removed, removed_at, added_by) " +
                        "SELECT id, book_uid, title, title_transliteration, author, " +
                        "title_sort, author_sort, isbn, publisher, published_year, " +
                        "genre, cover_url, page_count, language, notes, location, " +
                        "source_type, source_detail, " +
                        "CASE age_group " +
                        "WHEN 0 THEN 'above-3' " +
                        "WHEN 1 THEN 'above-6' " +
                        "WHEN 2 THEN 'above-10' " +
                        "WHEN 3 THEN 'advanced' " +
                        "ELSE NULL END, " +
                        "added_date, copy_count, needs_metadata, removed, " +
                        "removed_at, added_by FROM `books`"
                )

                db.execSQL("DROP TABLE `books`")
                db.execSQL("ALTER TABLE `books_new` RENAME TO `books`")

                // Re-create every index (names match Room's generated convention).
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_isbn` ON `books` (`isbn`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_book_uid` ON `books` (`book_uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_added_date` ON `books` (`added_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_needs_metadata` ON `books` (`needs_metadata`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_title_sort` ON `books` (`title_sort`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_author_sort` ON `books` (`author_sort`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_age_group` ON `books` (`age_group`)")

                // Recreate the FTS content-sync triggers (verbatim from the
                // generated v10 schema — identical to v9, age_group is not an FTS
                // column) and rebuild the index from the recreated content table.
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_UPDATE " +
                        "BEFORE UPDATE ON `books` BEGIN " +
                        "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_BEFORE_DELETE " +
                        "BEFORE DELETE ON `books` BEGIN " +
                        "DELETE FROM `books_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_UPDATE " +
                        "AFTER UPDATE ON `books` BEGIN " +
                        "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                        "`author`, `isbn`, `location`, `genre`) VALUES " +
                        "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                        "NEW.`author`, NEW.`isbn`, NEW.`location`, NEW.`genre`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_books_fts_AFTER_INSERT " +
                        "AFTER INSERT ON `books` BEGIN " +
                        "INSERT INTO `books_fts`(`docid`, `title`, `title_transliteration`, " +
                        "`author`, `isbn`, `location`, `genre`) VALUES " +
                        "(NEW.`rowid`, NEW.`title`, NEW.`title_transliteration`, " +
                        "NEW.`author`, NEW.`isbn`, NEW.`location`, NEW.`genre`); END"
                )
                db.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES('rebuild')")
            }
        }
    }
}
