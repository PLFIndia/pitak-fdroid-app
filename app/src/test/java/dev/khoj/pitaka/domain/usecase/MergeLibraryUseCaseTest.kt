package dev.khoj.pitaka.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.import_.ImportFormatSniffer
import dev.khoj.pitaka.data.import_.PitakaJsonImporter
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.domain.repository.LibraryFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plain Application so Robolectric doesn't open the real PitakaApplication's
 *  DataStore on the same file (DataStore forbids two instances per file). */
class MergeStubApp : android.app.Application()

/**
 * Tests for [MergeLibraryUseCase] with the D40 library-ID gate + D41 attribution.
 * Robolectric for the DataStore-backed [AppPreferences]; real importer/sniffer/Moshi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MergeStubApp::class)
class MergeLibraryUseCaseTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val importer = PitakaJsonImporter(moshi)
    private val sniffer = ImportFormatSniffer()
    private lateinit var prefs: AppPreferences

    // Valid library IDs (32-char lowercase hex, the minted shape). The old test
    // used "LIB-A"/"LIB-B", which are now rejected by the LibraryId guard — using
    // realistic IDs keeps the harness honest against the validation we enforce.
    private val libA = "a".repeat(32)
    private val libB = "b".repeat(32)

    @Before
    fun setUp() = runBlocking {
        prefs = AppPreferences(ApplicationProvider.getApplicationContext<Context>())
        // DataStore file is shared across tests in the same Robolectric process;
        // reset the namespace fields so each test starts from a known state.
        prefs.setLibraryId("")
        prefs.setLibraryName("")
        prefs.setMaintainerName("")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun export(libraryId: String, libraryName: String, vararg books: Book): String =
        moshi.adapter<PitakaExport>().toJson(
            PitakaExport(
                exportedAt = 0L,
                books = books.toList(),
                wishlist = emptyList(),
                libraryId = libraryId,
                libraryName = libraryName,
            )
        )

    private fun book(
        id: Long = 0L, uid: String, title: String, author: String? = null,
        isbn: String? = null, genre: String? = null,
    ) = Book(
        id = id, bookUid = uid, title = title, author = author, isbn = isbn,
        genre = genre, addedDate = 1000L,
    )

    @Test
    fun matching_library_id_merges_and_auto_adds(): Unit = runBlocking {
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId(libA)
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val file = export(
            libA, "Riverside",
            book(uid = "u1", title = "Godaan", isbn = "111"),
            book(uid = "u2", title = "1984", isbn = "222"),
        )
        val outcome = useCase(file)

        assertThat(outcome).isInstanceOf(MergeLibraryUseCase.Outcome.Merged::class.java)
        val merged = (outcome as MergeLibraryUseCase.Outcome.Merged).result
        assertThat(merged.added).isEqualTo(1)
        assertThat(merged.identical).isEqualTo(1)
        assertThat(repo.snapshot().map { it.title }).containsExactly("Godaan", "1984")
    }

    @Test
    fun differing_library_id_returns_decision_not_a_merge(): Unit = runBlocking {
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId(libA)
        prefs.setLibraryName("My Shelf")
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val file = export(libB, "Riverside Community Library", book(uid = "u9", title = "New", isbn = "999"))
        val outcome = useCase(file)

        assertThat(outcome).isInstanceOf(MergeLibraryUseCase.Outcome.DiffersDecision::class.java)
        val d = outcome as MergeLibraryUseCase.Outcome.DiffersDecision
        assertThat(d.incomingLibraryName).isEqualTo("Riverside Community Library")
        assertThat(d.localLibraryName).isEqualTo("My Shelf")
        // Nothing applied yet.
        assertThat(repo.snapshot()).hasSize(1)
    }

    @Test
    fun join_unions_books_and_adopts_incoming_id(): Unit = runBlocking {
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId(libA)
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val file = export(libB, "Riverside", book(uid = "u9", title = "New", isbn = "999"))
        val outcome = useCase(file) as MergeLibraryUseCase.Outcome.DiffersDecision
        val merged = useCase.applyJoin(outcome)

        assertThat(merged.added).isEqualTo(1)
        assertThat(repo.snapshot().map { it.title }).containsExactly("Godaan", "New")
        // Adopted the incoming namespace.
        assertThat(prefs.getOrCreateLibraryId()).isEqualTo(libB)
    }

    @Test
    fun overwrite_replaces_local_and_adopts_id(): Unit = runBlocking {
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId(libA)
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val file = export(libB, "Riverside", book(uid = "u9", title = "Replica", isbn = "999"))
        val outcome = useCase(file) as MergeLibraryUseCase.Outcome.DiffersDecision
        useCase.applyOverwrite(outcome)

        // Local catalogue replaced entirely.
        assertThat(repo.snapshot().map { it.title }).containsExactly("Replica")
        assertThat(prefs.getOrCreateLibraryId()).isEqualTo(libB)
    }

    @Test
    fun incoming_file_without_library_id_is_treated_as_differ(): Unit = runBlocking {
        // Old v1/v2 export → blank libraryId → never a silent merge.
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId("LIB-A")
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val file = export("", "", book(uid = "u9", title = "New", isbn = "999"))
        val outcome = useCase(file)

        assertThat(outcome).isInstanceOf(MergeLibraryUseCase.Outcome.DiffersDecision::class.java)
    }

    @Test
    fun corrupt_incoming_library_id_is_treated_as_differ_not_a_silent_merge(): Unit = runBlocking {
        // A hand-crafted/corrupt export whose libraryId is junk (uppercase,
        // non-hex, oversized) must NOT match the local ID and must NOT be
        // adopted. It is normalised to absent → DiffersDecision, exactly like a
        // v1/v2 file with no ID. Honors the library-ID string limitation on the
        // file-import path the same way QR pairing does.
        val repo = FakeRepo(listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111")))
        prefs.setLibraryId(libA)
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val junkId = "'; DROP TABLE books;--" + "Z".repeat(9000)
        val file = export(junkId, "Evil", book(uid = "u9", title = "New", isbn = "999"))
        val outcome = useCase(file)

        assertThat(outcome).isInstanceOf(MergeLibraryUseCase.Outcome.DiffersDecision::class.java)
        val d = outcome as MergeLibraryUseCase.Outcome.DiffersDecision
        // The junk ID is never surfaced to the UI as the incoming namespace.
        assertThat(d.incomingLibraryId).isEmpty()
        // Adopting via Join must not persist junk — the local ID stands.
        useCase.applyJoin(d)
        assertThat(prefs.getOrCreateLibraryId()).isEqualTo(libA)
    }

    @Test
    fun wrong_format_fails(): Unit = runBlocking {
        val repo = FakeRepo(emptyList())
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)
        val csv = "Book Id,Title,Author,ISBN\n1,Dune,Herbert,9780441013593\n"
        val outcome = useCase(csv)
        assertThat(outcome).isInstanceOf(MergeLibraryUseCase.Outcome.Failed::class.java)
    }

    @Test
    fun take_theirs_overwrites_in_place_keeping_identity(): Unit = runBlocking {
        val local = book(id = 7, uid = "u1", title = "Godaan", isbn = "111", genre = "Fiction")
        val repo = FakeRepo(listOf(local))
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val incoming = book(id = 99, uid = "u1", title = "Godaan", isbn = "111", genre = "Classic")
        useCase.applyResolution(local, incoming, MergeLibraryUseCase.Resolution.TAKE_THEIRS)

        val row = repo.snapshot().single()
        assertThat(row.id).isEqualTo(7)
        assertThat(row.bookUid).isEqualTo("u1")
        assertThat(row.genre).isEqualTo("Classic")
    }

    @Test
    fun keep_both_on_a_uid_conflict_produces_two_rows_not_a_silent_loss(): Unit = runBlocking {
        // The conflict was surfaced because both books share bookUid "u1".
        // KEEP_BOTH must NOT reinsert that uid (UNIQUE) — the duplicate gets a
        // fresh identity, and BOTH rows survive (F-21: the original bug dropped
        // the incoming row entirely).
        val local = book(id = 7, uid = "u1", title = "Godaan", isbn = null, genre = "Fiction")
        val repo = FakeRepo(listOf(local))
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val incoming = book(id = 99, uid = "u1", title = "Godaan", isbn = null, genre = "Classic")
        useCase.applyResolution(local, incoming, MergeLibraryUseCase.Resolution.KEEP_BOTH)

        val rows = repo.snapshot()
        assertThat(rows).hasSize(2)
        // The original is untouched.
        assertThat(rows.any { it.id == 7L && it.bookUid == "u1" && it.genre == "Fiction" }).isTrue()
        // The duplicate got a fresh (non-colliding) uid.
        val dup = rows.single { it.id != 7L }
        assertThat(dup.genre).isEqualTo("Classic")
        assertThat(dup.bookUid).isNull()
    }

    @Test
    fun keep_both_on_an_isbn_conflict_drops_the_duplicate_isbn_and_keeps_both_rows(): Unit = runBlocking {
        // Conflict matched by ISBN "111" (UNIQUE). KEEP_BOTH keeps both rows;
        // the original retains the ISBN, the duplicate drops it (two rows must
        // never share one ISBN — D2). Original bug: the duplicate vanished.
        val local = book(id = 7, uid = "u1", title = "Godaan", isbn = "111", genre = "Fiction")
        val repo = FakeRepo(listOf(local))
        val useCase = MergeLibraryUseCase(sniffer, importer, repo, prefs, moshi)

        val incoming = book(id = 99, uid = "u2", title = "Godaan (2nd印)", isbn = "111", genre = "Classic")
        useCase.applyResolution(local, incoming, MergeLibraryUseCase.Resolution.KEEP_BOTH)

        val rows = repo.snapshot()
        assertThat(rows).hasSize(2)
        // Original keeps the ISBN.
        assertThat(rows.single { it.id == 7L }.isbn).isEqualTo("111")
        // Duplicate dropped the ISBN and got a fresh uid.
        val dup = rows.single { it.id != 7L }
        assertThat(dup.isbn).isNull()
        assertThat(dup.bookUid).isNull()
        assertThat(dup.genre).isEqualTo("Classic")
    }

    // --- minimal in-memory fake ---

    /**
     * In-memory [BookRepository] that emulates the two behaviours F-21 depends
     * on: the `books` table's UNIQUE `book_uid` / `isbn` indices, and Room's
     * `@Upsert` semantics (insert; on a unique-constraint conflict, fall back to
     * update-by-primary-key). A fake that keys only on `id` would neither
     * reproduce the original silent-loss bug nor prove the fix, so this models
     * the constraint that is the whole point of the finding.
     */
    private class FakeRepo(initial: List<Book>) : BookRepository {
        private val rows = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
        fun snapshot(): List<Book> = rows.toList()

        override suspend fun getAll(): List<Book> = rows.toList()
        override suspend fun upsert(book: Book): Long {
            // Explicit-id write → straight update/replace of that row.
            if (book.id != 0L) {
                val id = book.id
                rows.removeAll { it.id == id }
                rows += book
                return id
            }
            // id == 0 → INSERT. Room's @Upsert first tries an insert; a UNIQUE
            // collision (book_uid or isbn already present on another row) makes
            // it fall back to update-by-PK. The incoming PK is 0, which matches
            // no row, so the write is a no-op — the row silently vanishes. We
            // model exactly that so the fix is genuinely exercised.
            val collides = rows.any { existing ->
                (book.bookUid != null && existing.bookUid == book.bookUid) ||
                    (book.isbn != null && existing.isbn == book.isbn)
            }
            if (collides) return 0L // update-by-PK(0) matches nothing → dropped
            val id = nextId++
            rows += book.copy(id = id)
            return id
        }
        override suspend fun findByIsbn(isbn: String): Book? = rows.firstOrNull { it.isbn == isbn }
        override suspend fun findByUid(uid: String): Book? = rows.firstOrNull { it.bookUid == uid }
        override suspend fun getById(id: Long): Book? = rows.firstOrNull { it.id == id }
        override suspend fun delete(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun markRemoved(id: Long, at: Long) {
            rows.replaceAll { if (it.id == id) it.copy(removed = true, removedAt = at) else it }
        }
        override suspend fun restore(id: Long) {
            rows.replaceAll { if (it.id == id) it.copy(removed = false, removedAt = null) else it }
        }
        override fun observeAll(sort: BookSort, filter: LibraryFilter): Flow<List<Book>> = flowOf(rows)
        override fun observeLanguages(): Flow<List<String>> = flowOf(emptyList())
        override fun observeNeedsMetadata(): Flow<List<Book>> = flowOf(emptyList())
        override fun observeById(id: Long): Flow<Book?> = flowOf(rows.firstOrNull { it.id == id })
        override fun observeByIds(ids: List<Long>): Flow<List<Book>> = flowOf(rows.filter { it.id in ids })
        override fun search(query: String, sort: BookSort, filter: LibraryFilter): Flow<List<Book>> = flowOf(rows)
        override suspend fun seedRandomBooks(count: Int) = Unit
        override suspend fun deleteSeedBooks(): Int = 0
    }
}
