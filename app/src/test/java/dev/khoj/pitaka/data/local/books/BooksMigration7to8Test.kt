package dev.khoj.pitaka.data.local.books

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the v7→v8 migration SQL directly (PLAN-merge.md): adds `book_uid`
 * (UNIQUE, backfilled per existing row), `removed` (NOT NULL default 0), and
 * `removed_at` (nullable).
 *
 * Drives [BooksDatabase.MIGRATION_7_8] against a hand-built v7 `books` table on
 * Robolectric's SQLite — no MigrationTestHelper / schema-asset wiring needed
 * (the project exports schemas for on-device validation but keeps unit tests
 * asset-free). The v7 `books` DDL is copied verbatim from the exported
 * `schemas/.../7.json` so the starting shape is exactly what ships.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class BooksMigration7to8Test {

    // v7 `books` table DDL, verbatim from schemas/...BooksDatabase/7.json.
    private val createV7Books =
        "CREATE TABLE IF NOT EXISTS `books` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`title` TEXT NOT NULL, `title_transliteration` TEXT, `author` TEXT, " +
            "`title_sort` TEXT NOT NULL DEFAULT '', `author_sort` TEXT NOT NULL DEFAULT '', " +
            "`isbn` TEXT, `publisher` TEXT, `published_year` INTEGER, `genre` TEXT, " +
            "`cover_url` TEXT, `page_count` INTEGER, `language` TEXT, `notes` TEXT, " +
            "`location` TEXT, `source_type` TEXT, `source_detail` TEXT, `age_group` INTEGER, " +
            "`added_date` INTEGER NOT NULL, `copy_count` INTEGER NOT NULL DEFAULT 1, " +
            "`needs_metadata` INTEGER NOT NULL DEFAULT 0)"

    private lateinit var helper: SupportSQLiteOpenHelper

    private fun openV7(): SupportSQLiteDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cfg = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(createV7Books)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(cfg)
        return helper.writableDatabase
    }

    @After
    fun tearDown() {
        if (::helper.isInitialized) helper.close()
    }

    @Test
    fun migrate_7_to_8_adds_uid_removed_and_backfills_uid() {
        val db = openV7()
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata) " +
                "VALUES ('Godaan', 'godaan', 'premchand', 1000, 1, 0)"
        )
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata) " +
                "VALUES ('Sapiens', 'sapiens', 'harari', 2000, 1, 0)"
        )

        // --- Run the real migration ---
        BooksDatabase.MIGRATION_7_8.migrate(db)

        // Every pre-existing row got a non-blank, distinct uid; removed defaults
        // to 0 (active) with a null removed_at.
        val uids = mutableListOf<String>()
        db.query("SELECT book_uid, removed, removed_at FROM books ORDER BY added_date ASC").use { c ->
            while (c.moveToNext()) {
                val uid = c.getString(0)
                assertThat(uid).isNotEmpty()
                assertThat(c.getInt(1)).isEqualTo(0)
                assertThat(c.isNull(2)).isTrue()
                uids += uid
            }
        }
        assertThat(uids).hasSize(2)
        assertThat(uids.toSet()).hasSize(2) // backfill minted UNIQUE values

        // The UNIQUE index is enforced post-migration.
        var rejected = false
        try {
            db.execSQL(
                "INSERT INTO books (book_uid, title, title_sort, author_sort, added_date, copy_count, needs_metadata) " +
                    "VALUES ('${uids[0]}', 'Dup', 'dup', '', 3000, 1, 0)"
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertThat(rejected).isTrue()

        // A new active row can be inserted with its own uid and removed=0.
        db.execSQL(
            "INSERT INTO books (book_uid, title, title_sort, author_sort, added_date, copy_count, needs_metadata) " +
                "VALUES ('uid-new', 'New', 'new', '', 4000, 1, 0)"
        )
        db.query("SELECT removed FROM books WHERE book_uid = 'uid-new'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migrate_8_to_9_adds_added_by_nullable() {
        // Build v7, run 7→8, then 8→9 in sequence (the real upgrade path for a
        // device that skipped a version). Assert added_by exists and defaults null.
        val db = openV7()
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata) " +
                "VALUES ('Godaan', 'godaan', 'premchand', 1000, 1, 0)"
        )
        BooksDatabase.MIGRATION_7_8.migrate(db)
        BooksDatabase.MIGRATION_8_9.migrate(db)

        // Existing row has NULL attribution (added before the feature).
        db.query("SELECT added_by FROM books").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.isNull(0)).isTrue()
        }

        // A new row can carry an attribution.
        db.execSQL(
            "INSERT INTO books (book_uid, title, title_sort, author_sort, added_date, copy_count, needs_metadata, added_by) " +
                "VALUES ('uid-x', 'Kabir', 'kabir', '', 5000, 1, 0, 'Asha')"
        )
        db.query("SELECT added_by FROM books WHERE book_uid = 'uid-x'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Asha")
        }
    }

    @Test
    fun migrate_9_to_10_remaps_age_ordinal_to_token() {
        // Full upgrade path v7→…→9, seeding the v7 `age_group` INTEGER column with
        // every old ordinal plus a NULL, then run 9→10 and assert each ordinal
        // maps to its new TEXT token (B-decision: 0→above-3, 1→above-6,
        // 2→above-10, 3→advanced; NULL→NULL; nothing maps to above-15).
        val db = openV7()
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('Tot', 'tot', '', 1000, 1, 0, 0)"
        )
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('Six', 'six', '', 2000, 1, 0, 1)"
        )
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('Teen', 'teen', '', 3000, 1, 0, 2)"
        )
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('Adv', 'adv', '', 4000, 1, 0, 3)"
        )
        db.execSQL(
            "INSERT INTO books (title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('None', 'none', '', 5000, 1, 0, NULL)"
        )

        BooksDatabase.MIGRATION_7_8.migrate(db)
        BooksDatabase.MIGRATION_8_9.migrate(db)
        // The external-content FTS table exists from v6/v7 in a real DB; the
        // asset-free v7 harness omits it, so create it here to mirror v9 state
        // (MIGRATION_9_10 drops/recreates its triggers and rebuilds the index).
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `title_transliteration` TEXT, " +
                "`author` TEXT, `isbn` TEXT, `location` TEXT, `genre` TEXT, " +
                "content=`books`)"
        )
        BooksDatabase.MIGRATION_9_10.migrate(db)

        val tokens = mutableMapOf<String, String?>()
        db.query("SELECT title, age_group FROM books").use { c ->
            while (c.moveToNext()) {
                tokens[c.getString(0)] = if (c.isNull(1)) null else c.getString(1)
            }
        }
        assertThat(tokens["Tot"]).isEqualTo("above-3")
        assertThat(tokens["Six"]).isEqualTo("above-6")
        assertThat(tokens["Teen"]).isEqualTo("above-10")
        assertThat(tokens["Adv"]).isEqualTo("advanced")
        assertThat(tokens["None"]).isNull()
        // Nothing in the old data maps to the new top band.
        assertThat(tokens.values).doesNotContain("above-15")

        // Row count preserved (no rows dropped by the table-recreate).
        db.query("SELECT COUNT(*) FROM books").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(5)
        }

        // The age_group index survived the recreate.
        var hasIndex = false
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_books_age_group'").use { c ->
            hasIndex = c.moveToFirst()
        }
        assertThat(hasIndex).isTrue()

        // A new row can be written with a TEXT token after the migration.
        db.execSQL(
            "INSERT INTO books (book_uid, title, title_sort, author_sort, added_date, copy_count, needs_metadata, age_group) " +
                "VALUES ('uid-15', 'Fifteen', 'fifteen', '', 6000, 1, 0, 'above-15')"
        )
        db.query("SELECT age_group FROM books WHERE book_uid = 'uid-15'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("above-15")
        }
    }
}
