package dev.khoj.pitaka.data.images

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.data.local.books.BooksDatabase
import dev.khoj.pitaka.data.repository.BookRepositoryImpl
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Plain [android.app.Application] so Robolectric does NOT instantiate the real
 * [dev.khoj.pitaka.PitakaApplication], whose onCreate launches its own
 * CoverHealer against the shared filesDir — that would race this test's
 * healer. Using a stub app isolates the unit under test.
 */
class StubApp : android.app.Application()

/** In-memory [CoverHealFlag] so the test never stands up DataStore. */
private class FakeCoverHealFlag(private var healed: Boolean = false) : CoverHealFlag {
    override suspend fun isHealed(): Boolean = healed
    override suspend fun markHealed() { healed = true }
}

/**
 * Wave 4 integration test (PLAN-covers.md D4): drives the REAL [CoverHealer]
 * over a real in-memory Room DB + a real filesDir/covers directory, seeded
 * with the exact corruption captured from the device. Verifies the applier
 * (file renames, row rewrites, orphan deletes, flag) — not just the planner.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = StubApp::class)
class CoverHealerTest {

    private lateinit var ctx: Context
    private lateinit var db: BooksDatabase
    private lateinit var repo: BookRepository
    private lateinit var flag: FakeCoverHealFlag
    private lateinit var healer: CoverHealer
    private lateinit var coversDir: File

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, BooksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BookRepositoryImpl(db.bookDao())
        flag = FakeCoverHealFlag()
        healer = CoverHealer(ctx, repo, flag)
        coversDir = File(ctx.filesDir, CoverPaths.COVERS_DIR).apply {
            deleteRecursively(); mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        coversDir.deleteRecursively()
    }

    private fun writeCover(leaf: String) {
        File(coversDir, leaf).writeBytes(ByteArray(16) { 1 })
    }

    private fun legacy(n: Int) =
        "file:///data/user/0/dev.khoj.pitaka/files/covers/$n.jpg"

    @Test
    fun heals_the_device_fixture_end_to_end() = runBlocking {
        // Files present on disk (as on the device): 6, 7, 10.
        writeCover("6.jpg"); writeCover("7.jpg"); writeCover("10.jpg")

        // Insert rows mirroring the captured DB. Room assigns ids 1..N in
        // insertion order, so insert title-by-title to line ids up.
        val ids = listOf(
            Book(title = "tarkash", coverUrl = legacy(10)),                 // 1 shared→clear
            Book(title = "urdu shayari", coverUrl = legacy(9)),             // 2 missing→clear
            Book(title = "rekhta", coverUrl = legacy(8)),                   // 3 missing→clear
            Book(title = "guldasta", coverUrl = null),                      // 4 untouched
            Book(title = "har mauke", coverUrl = legacy(6)),                // 5 unique→salvage
            Book(title = "kabir", coverUrl = legacy(5)),                    // 6 missing→clear
            Book(title = "code", coverUrl = legacy(7)),                     // 7 unique→salvage
            Book(title = "ruling caste", coverUrl = "https://covers.openlibrary.org/b/id/1188894-M.jpg"),
            Book(title = "cs distilled", coverUrl = "https://covers.openlibrary.org/b/id/10194609-M.jpg"),
            Book(title = "cs unleashed", coverUrl = legacy(10)),            // 10 shared→clear
        ).map { repo.upsert(it) }

        val ran = healer.runIfNeeded()
        assertThat(ran).isTrue()

        val byId = repo.getAll().associateBy { it.id }
        fun cover(idx: Int) = byId[ids[idx]]!!.coverUrl

        // Cleared rows (shared or missing).
        assertThat(cover(0)).isNull() // tarkash (shared 10)
        assertThat(cover(1)).isNull() // missing 9
        assertThat(cover(2)).isNull() // missing 8
        assertThat(cover(5)).isNull() // kabir, missing 5
        assertThat(cover(9)).isNull() // cs unleashed (shared 10)

        // Untouched.
        assertThat(cover(3)).isNull() // was already null
        assertThat(cover(7)).isEqualTo("https://covers.openlibrary.org/b/id/1188894-M.jpg")
        assertThat(cover(8)).isEqualTo("https://covers.openlibrary.org/b/id/10194609-M.jpg")

        // Salvaged → now relative covers/<uuid>.jpg, and the file exists.
        val harMauke = cover(4)!!
        val code = cover(6)!!
        assertThat(harMauke).startsWith("covers/")
        assertThat(code).startsWith("covers/")
        assertThat(harMauke).isNotEqualTo(code)
        assertThat(File(coversDir, CoverPaths.leafOf(harMauke)!!).exists()).isTrue()
        assertThat(File(coversDir, CoverPaths.leafOf(code)!!).exists()).isTrue()

        // Legacy salvaged files renamed away; orphaned 10.jpg reclaimed.
        assertThat(File(coversDir, "6.jpg").exists()).isFalse()
        assertThat(File(coversDir, "7.jpg").exists()).isFalse()
        assertThat(File(coversDir, "10.jpg").exists()).isFalse()

        // Exactly two files remain (the two salvaged covers).
        assertThat(coversDir.listFiles()?.size).isEqualTo(2)

        // Flag set; a second run is a no-op.
        assertThat(flag.isHealed()).isTrue()
        assertThat(healer.runIfNeeded()).isFalse()
    }

    @Test
    fun clean_library_runs_once_and_sets_flag() = runBlocking {
        writeCover("good.jpg")
        repo.upsert(Book(title = "ok", coverUrl = "covers/good.jpg"))

        assertThat(healer.runIfNeeded()).isTrue()
        // Valid relative cover untouched; file still present.
        val b = repo.getAll().single()
        assertThat(b.coverUrl).isEqualTo("covers/good.jpg")
        assertThat(File(coversDir, "good.jpg").exists()).isTrue()
        assertThat(flag.isHealed()).isTrue()
    }
}
