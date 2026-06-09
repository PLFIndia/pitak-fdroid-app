package dev.khoj.pitaka.data.repository

import dev.khoj.pitaka.data.local.books.BookDao
import dev.khoj.pitaka.data.local.books.toDomain
import dev.khoj.pitaka.data.local.books.toEntity
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.domain.repository.LibraryFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val dao: BookDao,
) : BookRepository {

    override fun observeAll(sort: BookSort, filter: LibraryFilter): Flow<List<Book>> = when (sort) {
        BookSort.RecentlyAdded -> dao.observeAllByRecentlyAdded()
        BookSort.LanguageAsc   -> dao.observeAllByLanguage()
        BookSort.AgeGroupAsc   -> dao.observeAllByAgeGroup()
    }.map { list -> list.map { it.toDomain() }.applyFilter(filter) }

    override fun observeLanguages(): Flow<List<String>> = dao.observeDistinctLanguages()

    override fun observeNeedsMetadata(): Flow<List<Book>> =
        dao.observeNeedsMetadata().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Book?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeByIds(ids: List<Long>): Flow<List<Book>> =
        if (ids.isEmpty()) {
            // Avoid a needless "IN ()" query when there are no loans.
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            dao.observeByIds(ids).map { list -> list.map { it.toDomain() } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun search(query: String, sort: BookSort, filter: LibraryFilter): Flow<List<Book>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeAll(sort, filter)

        // Hybrid search (P1). Try the FTS prefix fast path first; fall back to the
        // substring LIKE scan when FTS isn't suitable or finds nothing. The
        // fallback preserves today's mid-word / mid-ISBN substring behaviour —
        // important for D8 Devanagari/Gurmukhi (FTS only matches token prefixes)
        // and partial ISBN lookups.
        //
        // Per-sort search is a deliberate simplification (Phase 1): results come
        // back recently-added-first regardless of `sort`; the user's mental model
        // of search is "latest match", not "alphabetise". The facet filter is
        // applied to the result set after the text match.
        val match = buildFtsMatch(trimmed)
        if (match == null) {
            // No usable FTS tokens (e.g. all punctuation, or too short) — go
            // straight to the substring scan.
            return dao.searchByRecentlyAdded(trimmed)
                .map { list -> list.map { it.toDomain() }.applyFilter(filter) }
        }

        return flow {
            val fts = dao.searchFts(match).first()
            val source = if (fts.isNotEmpty()) {
                dao.searchFts(match)
            } else {
                // FTS found nothing — fall back to substring LIKE so partial /
                // mid-token / numeric queries still match.
                dao.searchByRecentlyAdded(trimmed)
            }
            emitAll(source.map { list -> list.map { it.toDomain() }.applyFilter(filter) })
        }
    }

    override suspend fun getById(id: Long): Book? = dao.getById(id)?.toDomain()

    override suspend fun getAll(): List<Book> = dao.getAll().map { it.toDomain() }

    override suspend fun findByIsbn(isbn: String): Book? = dao.findByIsbn(isbn)?.toDomain()

    override suspend fun findByUid(uid: String): Book? = dao.findByUid(uid)?.toDomain()

    override suspend fun upsert(book: Book): Long = dao.upsert(book.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun markRemoved(id: Long, at: Long) = dao.markRemoved(id, at)

    override suspend fun restore(id: Long) = dao.restore(id)

    override suspend fun seedRandomBooks(count: Int) {
        if (count <= 0) return
        val now = System.currentTimeMillis()
        // Per-run nonce so repeated seeding never collides on the unique isbn
        // index — each run produces a fresh batch of unique ISBNs. Base36 keeps
        // it short. Still carries SEED_ISBN_PREFIX so deleteSeedBooks matches.
        val runNonce = now.toString(36)
        val titleWords = listOf(
            "Shadow", "River", "Empire", "Quantum", "Silent", "Crimson", "Northern",
            "Forgotten", "Midnight", "Broken", "Eternal", "Hidden", "किताब", "कहानी",
            "इतिहास", "ਕਿਤਾਬ", "ਕਹਾਣੀ", "Garden", "Machine", "Ocean", "Letters", "Atlas",
        )
        val nouns = listOf(
            "Throne", "Code", "Sea", "War", "Dream", "Light", "Stone", "Song",
            "Mirror", "Engine", "City", "संसार", "ਦੁਨੀਆ", "Journey", "Theory",
        )
        val firstNames = listOf(
            "Arundhati", "George", "Haruki", "Chimamanda", "Italo", "Premchand",
            "Amrita", "Salman", "Ursula", "Gabriel", "Toni", "Khushwant",
        )
        val lastNames = listOf(
            "Roy", "Orwell", "Murakami", "Adichie", "Calvino", "Pritam",
            "Rushdie", "Le Guin", "Marquez", "Morrison", "Singh", "Devi",
        )
        val batch = (0 until count).map { i ->
            val title = "${titleWords.random()} ${nouns.random()} ${i + 1}"
            val author = "${firstNames.random()} ${lastNames.random()}"
            // ISBN: sentinel prefix + per-run nonce + index. Non-numeric prefix
            // can never collide with a real ISBN; the nonce guarantees uniqueness
            // across repeated seed runs so the unique index never aborts.
            val isbn = "$SEED_ISBN_PREFIX$runNonce-$i"
            Book(
                title = title,
                author = author,
                isbn = isbn,
                publishedYear = 1950 + (i % 75),
                // Spread addedDate so RecentlyAdded sort has a stable order.
                addedDate = now - i.toLong() * 1000L,
            ).toEntity()
        }
        // Insert in chunks to keep each transaction bounded.
        batch.chunked(500).forEach { dao.insertAll(it) }
    }

    override suspend fun deleteSeedBooks(): Int =
        dao.deleteSeedBooks(
            sentinelPattern = "$SEED_ISBN_PREFIX%",
            // Legacy seeded ISBNs were 9781000000000..9781000009999 from an
            // earlier build; '978100000%' covers exactly that synthetic block.
            legacyPattern = "978100000%",
        )

    companion object {
        /**
         * Sentinel prefix on every debug-seeded book's ISBN. Non-numeric, so it
         * can never match a real ISBN — makes seeded rows surgically deletable
         * without any risk to real data.
         */
        const val SEED_ISBN_PREFIX = "SEEDTEST-"
    }
}

/**
 * Builds an FTS4 MATCH expression from a free-text query.
 *
 * Each whitespace-separated word becomes a prefix term (`word*`) so partial
 * typing matches token starts; multiple words are ANDed (FTS4 default). FTS
 * syntax characters are stripped from each token so user input can't form an
 * invalid MATCH expression (which would throw at query time). Tokens shorter
 * than 2 chars are dropped — single-char prefix terms match almost everything
 * and aren't worth the scan.
 *
 * Returns null when nothing usable remains (all punctuation, or only 1-char
 * tokens); the caller then uses the substring LIKE fallback instead.
 */
internal fun buildFtsMatch(query: String): String? {
    val terms = query
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[\"*^():\\-]"), "").trim() }
        .filter { it.length >= 2 }
    if (terms.isEmpty()) return null
    return terms.joinToString(" ") { "$it*" }
}

/**
 * Applies a [LibraryFilter]'s genre/language facets to an already-sorted list.
 * Exact match, case-insensitive on a trimmed value (mirrors how the chips are
 * derived from the DISTINCT DAO queries). No-op when the filter is inactive, so
 * the common unfiltered path allocates nothing extra.
 */
internal fun List<Book>.applyFilter(filter: LibraryFilter): List<Book> {
    if (!filter.isActive) return this
    val language = filter.language?.trim()?.lowercase()
    return filter { b ->
        language == null || b.language?.trim()?.lowercase() == language
    }
}
