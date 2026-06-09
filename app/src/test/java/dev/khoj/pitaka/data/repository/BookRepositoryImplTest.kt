package dev.khoj.pitaka.data.repository

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.data.local.books.BookDao
import dev.khoj.pitaka.data.local.books.BookEntity
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Pure-JVM repository test driven by a [FakeBookDao]. No Room, no Android
 * framework — exercises the [BookRepositoryImpl] mapping and sort routing.
 */
class BookRepositoryImplTest {

    @Test
    fun observeAll_routes_to_recently_added_dao_query_by_default() = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Old",  addedDate = 1_000L),
            entity(id = 2, title = "New",  addedDate = 3_000L),
            entity(id = 3, title = "Mid",  addedDate = 2_000L),
        )
        val repo = BookRepositoryImpl(fake)
        val titles = repo.observeAll().first().map { it.title }
        assertThat(titles).containsExactly("New", "Mid", "Old").inOrder()
    }

    @Test
    fun observeAll_routes_to_language_query_when_requested() = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Two", language = "Hindi"),
            entity(id = 2, title = "One", language = "English"),
        )
        val repo = BookRepositoryImpl(fake)
        val langs = repo.observeAll(BookSort.LanguageAsc).first().map { it.language }
        assertThat(langs).containsExactly("English", "Hindi").inOrder()
    }

    @Test
    fun search_with_blank_query_falls_back_to_observe_all() = runBlocking {
        val fake = FakeBookDao()
        fake.seed(entity(id = 1, title = "One", addedDate = 1L))
        val repo = BookRepositoryImpl(fake)
        val out = repo.search("   ").first()
        assertThat(out).hasSize(1)
        assertThat(out.first().title).isEqualTo("One")
    }

    @Test
    fun search_matches_title_substring_case_insensitively(): Unit = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Sapiens"),
            entity(id = 2, title = "Wittgenstein's Tractatus"),
        )
        val repo = BookRepositoryImpl(fake)
        val hits = repo.search("sapi").first()
        assertThat(hits.map { it.title }).containsExactly("Sapiens")
    }

    @Test
    fun search_uses_fts_prefix_fast_path_for_word_prefix(): Unit = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Sapiens", addedDate = 2L),
            entity(id = 2, title = "Sapient Machines", addedDate = 1L),
            entity(id = 3, title = "Tractatus"),
        )
        val repo = BookRepositoryImpl(fake)
        // "sapi" is a token prefix of both "Sapiens" and "Sapient" -> FTS hits.
        val hits = repo.search("sapi").first().map { it.title }
        assertThat(hits).containsExactly("Sapiens", "Sapient Machines").inOrder()
    }

    @Test
    fun search_falls_back_to_substring_when_fts_misses_mid_token(): Unit = runBlocking {
        val fake = FakeBookDao()
        fake.seed(entity(id = 1, title = "Wittgenstein"))
        val repo = BookRepositoryImpl(fake)
        // "genstein" is NOT a token prefix, so FTS returns nothing; the LIKE
        // fallback still finds it as a substring. This guards the bilingual /
        // mid-word case the hybrid design exists to protect.
        val hits = repo.search("genstein").first().map { it.title }
        assertThat(hits).containsExactly("Wittgenstein")
    }

    @Test
    fun search_falls_back_to_substring_for_partial_isbn(): Unit = runBlocking {
        val fake = FakeBookDao()
        fake.seed(entity(id = 1, title = "T", isbn = "9780143458289"))
        val repo = BookRepositoryImpl(fake)
        // A chunk from the middle of the ISBN: not a token prefix, LIKE catches it.
        val hits = repo.search("434582").first().map { it.title }
        assertThat(hits).containsExactly("T")
    }

    @Test
    fun upsert_returns_id_and_persists() = runBlocking {
        val fake = FakeBookDao()
        val repo = BookRepositoryImpl(fake)
        val id = repo.upsert(Book(title = "Hello"))
        assertThat(id).isGreaterThan(0L)
        assertThat(repo.getById(id)?.title).isEqualTo("Hello")
    }

    @Test
    fun findByIsbn_returns_match() = runBlocking {
        val fake = FakeBookDao()
        val repo = BookRepositoryImpl(fake)
        repo.upsert(Book(title = "T", isbn = "9780000000001"))
        assertThat(repo.findByIsbn("9780000000001")?.title).isEqualTo("T")
        assertThat(repo.findByIsbn("nope")).isNull()
    }

    @Test
    fun delete_removes_book() = runBlocking {
        val fake = FakeBookDao()
        val repo = BookRepositoryImpl(fake)
        val id = repo.upsert(Book(title = "Bye"))
        repo.delete(id)
        assertThat(repo.getById(id)).isNull()
    }

    @Test
    fun observeNeedsMetadata_returns_only_flagged_books_newest_first() = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Complete",  addedDate = 1_000L, needsMetadata = false),
            entity(id = 2, title = "Skeleton A", addedDate = 3_000L, needsMetadata = true),
            entity(id = 3, title = "Skeleton B", addedDate = 2_000L, needsMetadata = true),
        )
        val repo = BookRepositoryImpl(fake)
        val titles = repo.observeNeedsMetadata().first().map { it.title }
        assertThat(titles).containsExactly("Skeleton A", "Skeleton B").inOrder()
    }

    @Test
    fun observeAll_with_language_filter_returns_only_matching_language(): Unit = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Dune", language = "English"),
            entity(id = 2, title = "Sapiens", language = "English"),
            entity(id = 3, title = "Godaan", language = "hindi"),
        )
        val repo = BookRepositoryImpl(fake)
        // Language match is case-insensitive ("Hindi" matches "hindi").
        val filtered = repo.observeAll(
            filter = dev.khoj.pitaka.domain.repository.LibraryFilter(language = "Hindi"),
        ).first()
        assertThat(filtered.map { it.title }).containsExactly("Godaan")
    }

    @Test
    fun observeAll_genre_is_search_only_not_a_filter(): Unit = runBlocking {
        // Genre is matched through search, never via LibraryFilter.
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "Dune", genre = "Science Fiction", language = "English"),
            entity(id = 2, title = "Sapiens", genre = "History", language = "English"),
        )
        val repo = BookRepositoryImpl(fake)
        val hits = repo.search("science").first()
        assertThat(hits.map { it.title }).containsExactly("Dune")
    }

    @Test
    fun observeLanguages_returns_distinct_nonblank_sorted() = runBlocking {
        val fake = FakeBookDao()
        fake.seed(
            entity(id = 1, title = "A", language = "English"),
            entity(id = 2, title = "B", language = "Hindi"),
            entity(id = 3, title = "C", language = ""),
        )
        val repo = BookRepositoryImpl(fake)
        assertThat(repo.observeLanguages().first()).containsExactly("English", "Hindi").inOrder()
    }

    @Test
    fun seedRandomBooks_then_deleteSeedBooks_leaves_real_books_untouched(): Unit = runBlocking {
        val fake = FakeBookDao()
        // Two real books with normal ISBNs (one starts with 978 but is NOT in the
        // synthetic 978100000x block — proves the legacy matcher is tight, not a
        // broad "978%" sweep) plus one leftover legacy-format seeded row.
        fake.seed(
            entity(id = 1, title = "Real Book", isbn = "9780140428445"),
            entity(id = 2, title = "Another Real", isbn = "9789388183086"),
            entity(id = 3, title = "Legacy Seed", isbn = "9781000000042"),
        )
        val repo = BookRepositoryImpl(fake)

        repo.seedRandomBooks(50) // current SEEDTEST- scheme
        assertThat(fake.count()).isEqualTo(53) // 2 real + 1 legacy + 50 new

        val removed = repo.deleteSeedBooks()
        assertThat(removed).isEqualTo(51) // 50 new + 1 legacy
        val remaining = repo.observeAll().first()
        assertThat(remaining.map { it.title }).containsExactly("Real Book", "Another Real")
    }

    // --- helpers ---

    private fun entity(
        id: Long, title: String,
        author: String? = null,
        isbn: String? = null,
        addedDate: Long = 0L,
        location: String? = null,
        genre: String? = null,
        language: String? = null,
        ageGroup: String? = null,
        needsMetadata: Boolean = false,
    ) = BookEntity(
        id = id, title = title,
        titleTransliteration = null, author = author, isbn = isbn,
        publisher = null, publishedYear = null, genre = genre, coverUrl = null,
        pageCount = null, language = language, notes = null, location = location,
        ageGroup = ageGroup,
        addedDate = addedDate, copyCount = 1, needsMetadata = needsMetadata,
    )
}

/**
 * Minimal in-memory DAO substitute. Mimics the ORDER BY clauses of [BookDao]
 * closely enough that the repository's sort/search routing can be verified
 * without Room.
 */
private class FakeBookDao : BookDao {
    private val rows = MutableStateFlow<List<BookEntity>>(emptyList())
    private var nextId: Long = 1L

    fun seed(vararg entities: BookEntity) {
        nextId = (entities.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value = entities.toList()
    }

    override fun observeAllByRecentlyAdded(): Flow<List<BookEntity>> =
        rows.map { list -> list.sortedWith(compareByDescending<BookEntity> { it.addedDate }.thenByDescending { it.id }) }

    override fun observeAllByLanguage(): Flow<List<BookEntity>> =
        rows.map { list ->
            list.sortedWith(
                compareBy<BookEntity> { it.language.isNullOrBlank() }
                    .thenBy { (it.language ?: "").lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
        }

    override fun observeAllByAgeGroup(): Flow<List<BookEntity>> =
        rows.map { list ->
            list.sortedWith(
                // Nulls last, then by band rank (mirrors BookDao's CASE), then title.
                compareBy<BookEntity> { it.ageGroup == null }
                    .thenBy { ageRank(it.ageGroup) }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
        }

    override fun searchByRecentlyAdded(q: String): Flow<List<BookEntity>> {
        val needle = q.lowercase()
        return rows.map { list ->
            list.filter {
                it.title.lowercase().contains(needle) ||
                (it.titleTransliteration?.lowercase()?.contains(needle) == true) ||
                (it.author?.lowercase()?.contains(needle) == true) ||
                (it.isbn?.lowercase()?.contains(needle) == true) ||
                (it.location?.lowercase()?.contains(needle) == true) ||
                (it.genre?.lowercase()?.contains(needle) == true)
            }.sortedWith(compareByDescending<BookEntity> { it.addedDate }.thenByDescending { it.id })
        }
    }

    // Mimics FTS4 prefix-token matching closely enough to exercise the
    // repository's hybrid routing: each "term*" matches a whitespace-delimited
    // word that STARTS WITH term (token prefix), ANDed across terms. Searches the
    // same columns the real FTS table indexes (incl. genre as of v7).
    override fun searchFts(match: String): Flow<List<BookEntity>> {
        val prefixes = match.split(Regex("\\s+"))
            .map { it.removeSuffix("*").lowercase() }
            .filter { it.isNotEmpty() }
        return rows.map { list ->
            list.filter { e ->
                val haystack = listOfNotNull(
                    e.title, e.titleTransliteration, e.author, e.isbn, e.location, e.genre,
                ).joinToString(" ").lowercase()
                val tokens = haystack.split(Regex("\\s+"))
                prefixes.all { p -> tokens.any { it.startsWith(p) } }
            }.sortedWith(compareByDescending<BookEntity> { it.addedDate }.thenByDescending { it.id })
        }
    }

    override suspend fun getById(id: Long): BookEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<BookEntity> = rows.value.sortedBy { it.id }

    override fun observeById(id: Long): Flow<BookEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override fun observeByIds(ids: List<Long>): Flow<List<BookEntity>> =
        rows.map { list -> list.filter { it.id in ids } }

    override fun observeNeedsMetadata(): Flow<List<BookEntity>> =
        rows.map { list ->
            list.filter { it.needsMetadata }
                .sortedWith(compareByDescending<BookEntity> { it.addedDate }.thenByDescending { it.id })
        }

    override suspend fun findByIsbn(isbn: String): BookEntity? =
        rows.value.firstOrNull { it.isbn == isbn }

    override suspend fun findByUid(uid: String): BookEntity? =
        rows.value.firstOrNull { it.bookUid == uid }

    override suspend fun markRemoved(id: Long, at: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(removed = true, removedAt = at) else it
        }
    }

    override suspend fun restore(id: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(removed = false, removedAt = null) else it
        }
    }

    override suspend fun upsert(book: BookEntity): Long {
        val effectiveId = if (book.id == 0L) nextId++ else book.id
        val updated = book.copy(id = effectiveId)
        rows.value = rows.value.filterNot { it.id == effectiveId } + updated
        return effectiveId
    }

    override suspend fun insert(book: BookEntity): Long = upsert(book)

    override suspend fun insertAll(books: List<BookEntity>) {
        books.forEach { upsert(it) }
    }

    override suspend fun update(book: BookEntity) {
        rows.value = rows.value.map { if (it.id == book.id) book else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteSeedBooks(sentinelPattern: String, legacyPattern: String): Int {
        // Mimic SQL LIKE 'prefix%' for both patterns (the only shape the repo uses).
        val sentinel = sentinelPattern.removeSuffix("%")
        val legacy = legacyPattern.removeSuffix("%")
        val before = rows.value.size
        rows.value = rows.value.filterNot { e ->
            val isbn = e.isbn ?: return@filterNot false
            isbn.startsWith(sentinel) || isbn.startsWith(legacy)
        }
        return before - rows.value.size
    }

    override suspend fun count(): Int = rows.value.size

    override fun observeDistinctLanguages(): Flow<List<String>> =
        rows.map { list ->
            list.mapNotNull { it.language?.trim()?.ifBlank { null } }
                .distinct()
                .sortedBy { it.lowercase() }
        }
}

/**
 * Band rank for an age-group token, mirroring BookDao.observeAllByAgeGroup's
 * CASE and Book.AgeGroup.sortRank. Unknown/blank ranks last (99).
 */
private fun ageRank(token: String?): Int = when (token) {
    "above-3" -> 0
    "above-6" -> 1
    "above-10" -> 2
    "above-15" -> 3
    "advanced" -> 4
    else -> 99
}
