package dev.khoj.pitaka.data.local.books

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pure-JVM Room test using AndroidX Room's in-memory builder.
 *
 * Robolectric isn't strictly required — Room exposes `inMemoryDatabaseBuilder`
 * usable on the JVM — but we run with [AndroidJUnit4] + Robolectric to keep the
 * door open for tests that need Context.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class BookDaoTest {

    private lateinit var db: BooksDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BooksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_then_getById_returns_inserted_book() = runBlocking {
        val id = dao.upsert(sampleEntity(title = "Godaan", author = "Premchand"))
        val fetched = dao.getById(id)
        assertThat(fetched).isNotNull()
        assertThat(fetched!!.title).isEqualTo("Godaan")
        assertThat(fetched.author).isEqualTo("Premchand")
    }

    @Test
    fun observeAllByRecentlyAdded_returns_newest_first() = runBlocking {
        dao.upsert(sampleEntity(title = "Old",  addedDate = 1_000L))
        dao.upsert(sampleEntity(title = "Mid",  addedDate = 2_000L))
        dao.upsert(sampleEntity(title = "New",  addedDate = 3_000L))

        val out = dao.observeAllByRecentlyAdded().first()
        assertThat(out.map { it.title }).containsExactly("New", "Mid", "Old").inOrder()
    }

    @Test
    fun observeAllByLanguage_sorts_alpha_blanks_last() = runBlocking {
        dao.upsert(sampleEntity(title = "B", language = "Hindi"))
        dao.upsert(sampleEntity(title = "A", language = "English"))
        dao.upsert(sampleEntity(title = "C", language = null))

        val langs = dao.observeAllByLanguage().first().map { it.language }
        // English, Hindi, then the null (blank) last.
        assertThat(langs).containsExactly("English", "Hindi", null).inOrder()
    }

    @Test
    fun observeAllByAgeGroup_sorts_by_band_rank_nulls_last() = runBlocking {
        // Insert out of order. Tokens stored as TEXT; the DAO must order by band
        // rank (above-3 < above-6 < above-10 < above-15 < advanced), NOT by the
        // token's alphabetical value (which would put "above-10" before "above-3").
        dao.upsert(sampleEntity(title = "Adv", ageGroup = "advanced"))
        dao.upsert(sampleEntity(title = "Tot", ageGroup = "above-3"))
        dao.upsert(sampleEntity(title = "Ten", ageGroup = "above-10"))
        dao.upsert(sampleEntity(title = "Fif", ageGroup = "above-15"))
        dao.upsert(sampleEntity(title = "None", ageGroup = null))

        val order = dao.observeAllByAgeGroup().first().map { it.title }
        assertThat(order).containsExactly("Tot", "Ten", "Fif", "Adv", "None").inOrder()
    }

    @Test
    fun searchByRecentlyAdded_matches_genre(): Unit = runBlocking {
        dao.upsert(sampleEntity(title = "Dune", genre = "Science Fiction"))
        dao.upsert(sampleEntity(title = "Sapiens", genre = "History"))

        val hits = dao.searchByRecentlyAdded("fiction").first()
        assertThat(hits.map { it.title }).containsExactly("Dune")
    }

    @Test
    fun searchByRecentlyAdded_matches_title_transliteration_and_isbn() = runBlocking {
        dao.upsert(sampleEntity(title = "गोदान",  titleTranslit = "Godaan",  isbn = "9780000000001"))
        dao.upsert(sampleEntity(title = "Sapiens", author = "Yuval Harari", isbn = "9780000000002"))
        dao.upsert(sampleEntity(title = "Tractatus", author = "Wittgenstein"))

        // Roman-script search hits the transliteration field (D8).
        val byTranslit = dao.searchByRecentlyAdded("godaan").first()
        assertThat(byTranslit.map { it.title }).containsExactly("गोदान")

        // ISBN substring.
        val byIsbn = dao.searchByRecentlyAdded("002").first()
        assertThat(byIsbn.map { it.title }).containsExactly("Sapiens")

        // Author substring.
        val byAuthor = dao.searchByRecentlyAdded("Witt").first()
        assertThat(byAuthor.map { it.title }).containsExactly("Tractatus")

        // No match → empty list, not an error (D26 — search has its own empty state).
        val none = dao.searchByRecentlyAdded("zzzzz").first()
        assertThat(none).isEmpty()
    }

    @Test
    fun searchByRecentlyAdded_matches_location(): Unit = runBlocking {
        dao.upsert(sampleEntity(title = "Sapiens", location = "Living room shelf 3"))
        dao.upsert(sampleEntity(title = "Tractatus"))

        val hits = dao.searchByRecentlyAdded("shelf 3").first()
        assertThat(hits.map { it.title }).containsExactly("Sapiens")
    }

    @Test
    fun findByIsbn_returns_match_or_null() = runBlocking {
        dao.upsert(sampleEntity(title = "Sapiens", isbn = "9780000000002"))
        assertThat(dao.findByIsbn("9780000000002")?.title).isEqualTo("Sapiens")
        assertThat(dao.findByIsbn("0000000000000")).isNull()
    }

    @Test
    fun deleteById_removes_row() = runBlocking {
        val id = dao.upsert(sampleEntity(title = "Throwaway"))
        assertThat(dao.count()).isEqualTo(1)
        dao.deleteById(id)
        assertThat(dao.count()).isEqualTo(0)
    }

    @Test
    fun upsert_with_existing_id_updates_in_place() = runBlocking {
        val id = dao.upsert(sampleEntity(title = "v1"))
        val updated = sampleEntity(id = id, title = "v2", addedDate = 1_234L)
        dao.upsert(updated)
        val fetched = dao.getById(id)
        assertThat(fetched!!.title).isEqualTo("v2")
        assertThat(fetched.addedDate).isEqualTo(1_234L)
        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun source_fields_round_trip_through_dao_and_mapper() = runBlocking {
        val id = dao.upsert(
            sampleEntity(
                title = "Gift book",
                sourceType = dev.khoj.pitaka.domain.model.Book.SourceType.GIFT.name,
                sourceDetail = "उपहार from Ravi",
            )
        )
        val domain = dao.getById(id)!!.toDomain()
        assertThat(domain.sourceType).isEqualTo(dev.khoj.pitaka.domain.model.Book.SourceType.GIFT)
        assertThat(domain.sourceDetail).isEqualTo("उपहार from Ravi")
        // Domain → entity → domain preserves the enum and detail.
        val reEntity = domain.toEntity()
        assertThat(reEntity.sourceType).isEqualTo("GIFT")
        assertThat(reEntity.toDomain().sourceType)
            .isEqualTo(dev.khoj.pitaka.domain.model.Book.SourceType.GIFT)
    }

    @Test
    fun source_type_unknown_stored_value_maps_to_null_not_crash() = runBlocking {
        // A row written by a future build with an unrecognized enum name must
        // degrade to null on read, never throw.
        val id = dao.upsert(sampleEntity(title = "Future", sourceType = "TIME_TRAVEL"))
        val domain = dao.getById(id)!!.toDomain()
        assertThat(domain.sourceType).isNull()
    }

    private fun sampleEntity(
        id: Long = 0L,
        title: String = "Title",
        titleTranslit: String? = null,
        author: String? = null,
        isbn: String? = null,
        addedDate: Long = System.currentTimeMillis(),
        copyCount: Int = 1,
        location: String? = null,
        sourceType: String? = null,
        sourceDetail: String? = null,
        genre: String? = null,
        language: String? = null,
        ageGroup: String? = null,
    ) = BookEntity(
        id = id,
        title = title,
        titleTransliteration = titleTranslit,
        author = author,
        // Mirror Book.toEntity(): the DAO is always fed entities whose sort keys
        // were computed by the mapper. Tests insert entities directly, so we
        // replicate that here to exercise the indexed title_sort/author_sort sort.
        titleSort = title.trim().lowercase(),
        authorSort = author?.trim()?.lowercase().orEmpty(),
        isbn = isbn,
        publisher = null,
        publishedYear = null,
        genre = genre,
        coverUrl = null,
        pageCount = null,
        language = language,
        notes = null,
        location = location,
        sourceType = sourceType,
        sourceDetail = sourceDetail,
        ageGroup = ageGroup,
        addedDate = addedDate,
        copyCount = copyCount,
    )
}
