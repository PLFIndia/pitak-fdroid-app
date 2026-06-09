package dev.khoj.pitaka.data.remote

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.local.cache.IsbnMetadataCacheDao
import dev.khoj.pitaka.data.local.cache.IsbnMetadataCacheEntity
import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.TitleSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException

class ChainedIsbnLookupTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun cache_hit_within_ttl_short_circuits_providers() = runBlocking {
        val cache = FakeCache()
        val payload = moshi.adapter(BookMetadata::class.java).toJson(meta("978", title = "Cached"))
        cache.upsert(IsbnMetadataCacheEntity("978", payload, "primary", fetchedAt = 1_000L))

        val primary = RecordingService(LookupResult.NotFound)
        val fallback = RecordingService(LookupResult.NotFound)
        val sut = ChainedIsbnLookup(primary, fallback, cache, moshi, clock = { 2_000L })

        val r = sut.lookupByIsbn("978")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        assertThat((r as LookupResult.Found).metadata.title).isEqualTo("Cached")
        assertThat(primary.lookupCalls).isEqualTo(0)
        assertThat(fallback.lookupCalls).isEqualTo(0)
    }

    @Test
    fun expired_cache_falls_through_to_providers() = runBlocking {
        val cache = FakeCache()
        val payload = moshi.adapter(BookMetadata::class.java).toJson(meta("978", title = "Old"))
        cache.upsert(IsbnMetadataCacheEntity("978", payload, "primary", fetchedAt = 0L))

        val freshMeta = meta("978", title = "Fresh")
        val primary = RecordingService(LookupResult.Found(freshMeta))
        val fallback = RecordingService(LookupResult.NotFound)
        val now = 31L * 24 * 60 * 60 * 1000 + 1
        val sut = ChainedIsbnLookup(primary, fallback, cache, moshi, clock = { now })

        val r = sut.lookupByIsbn("978")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        assertThat((r as LookupResult.Found).metadata.title).isEqualTo("Fresh")
        assertThat(primary.lookupCalls).isEqualTo(1)
    }

    @Test
    fun primary_NotFound_falls_through_to_fallback() = runBlocking {
        val cache = FakeCache()
        val primary = RecordingService(LookupResult.NotFound)
        val fallbackMeta = meta("978", title = "FromFallback")
        val fallback = RecordingService(LookupResult.Found(fallbackMeta))
        val sut = ChainedIsbnLookup(primary, fallback, cache, moshi, clock = { 1L })

        val r = sut.lookupByIsbn("978")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        assertThat((r as LookupResult.Found).metadata.title).isEqualTo("FromFallback")
        assertThat(primary.lookupCalls).isEqualTo(1)
        assertThat(fallback.lookupCalls).isEqualTo(1)
    }

    @Test
    fun primary_NetworkError_falls_through_to_fallback() = runBlocking {
        val cache = FakeCache()
        val primary = RecordingService(LookupResult.NetworkError(IOException("offline")))
        val fallbackMeta = meta("978", title = "FromFallback")
        val fallback = RecordingService(LookupResult.Found(fallbackMeta))
        val sut = ChainedIsbnLookup(primary, fallback, cache, moshi, clock = { 1L })

        val r = sut.lookupByIsbn("978")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        assertThat((r as LookupResult.Found).metadata.title).isEqualTo("FromFallback")
    }

    @Test
    fun both_NotFound_returns_NotFound() = runBlocking {
        val sut = ChainedIsbnLookup(
            primary = RecordingService(LookupResult.NotFound),
            fallback = RecordingService(LookupResult.NotFound),
            cacheDao = FakeCache(),
            moshi = moshi,
            clock = { 1L },
        )
        assertThat(sut.lookupByIsbn("978")).isEqualTo(LookupResult.NotFound)
    }

    @Test
    fun primary_error_plus_fallback_NotFound_yields_NetworkError() = runBlocking {
        val sut = ChainedIsbnLookup(
            primary = RecordingService(LookupResult.NetworkError(IOException("a"))),
            fallback = RecordingService(LookupResult.NotFound),
            cacheDao = FakeCache(),
            moshi = moshi,
            clock = { 1L },
        )
        assertThat(sut.lookupByIsbn("978")).isInstanceOf(LookupResult.NetworkError::class.java)
    }

    @Test
    fun found_response_is_written_through_to_cache() = runBlocking {
        val cache = FakeCache()
        val meta = meta("978", title = "Fresh")
        val sut = ChainedIsbnLookup(
            primary = RecordingService(LookupResult.Found(meta)),
            fallback = RecordingService(LookupResult.NotFound),
            cacheDao = cache,
            moshi = moshi,
            clock = { 12_345L },
        )
        sut.lookupByIsbn("978")
        val cached = cache.get("978")
        assertThat(cached).isNotNull()
        assertThat(cached!!.fetchedAt).isEqualTo(12_345L)
    }

    @Test
    fun refresh_bypasses_cache_and_writes_again() = runBlocking {
        val cache = FakeCache()
        val staleJson = moshi.adapter(BookMetadata::class.java).toJson(meta("978", title = "Stale"))
        cache.upsert(IsbnMetadataCacheEntity("978", staleJson, "primary", fetchedAt = 1L))

        val freshMeta = meta("978", title = "Fresh")
        val primary = RecordingService(LookupResult.Found(freshMeta))
        val sut = ChainedIsbnLookup(
            primary = primary,
            fallback = RecordingService(LookupResult.NotFound),
            cacheDao = cache,
            moshi = moshi,
            clock = { 100L },
        )
        val r = sut.refresh("978")
        assertThat((r as LookupResult.Found).metadata.title).isEqualTo("Fresh")
        assertThat(primary.lookupCalls).isEqualTo(1)
    }

    @Test
    fun searchByTitle_dedups_by_isbn() = runBlocking {
        val primary = RecordingService(
            searchResult = SearchResult.Found(
                listOf(
                    TitleSearchResult("k1", "Book A", isbn = "9780000000001"),
                    TitleSearchResult("k2", "Book A (alt edition)", isbn = "9780000000001"),
                    TitleSearchResult("k3", "Book B", isbn = "9780000000002"),
                )
            )
        )
        val sut = ChainedIsbnLookup(
            primary = primary,
            fallback = RecordingService(SearchResult.Empty.let { LookupResult.NotFound }, searchResult = SearchResult.Empty),
            cacheDao = FakeCache(),
            moshi = moshi,
            clock = { 1L },
        )
        val r = sut.searchByTitle("anything")
        assertThat(r).isInstanceOf(SearchResult.Found::class.java)
        assertThat((r as SearchResult.Found).results.map { it.isbn })
            .containsExactly("9780000000001", "9780000000002").inOrder()
    }

    @Test
    fun searchByTitle_primary_NetworkError_falls_to_fallback() = runBlocking {
        val primary = RecordingService(searchResult = SearchResult.NetworkError(IOException("x")))
        val fallback = RecordingService(
            searchResult = SearchResult.Found(
                listOf(TitleSearchResult("k", "Book", isbn = "9780000000001"))
            )
        )
        val sut = ChainedIsbnLookup(primary, fallback, FakeCache(), moshi, clock = { 1L })
        val r = sut.searchByTitle("x")
        assertThat(r).isInstanceOf(SearchResult.Found::class.java)
        assertThat((r as SearchResult.Found).results.first().title).isEqualTo("Book")
    }

    // --- helpers ---

    private fun meta(isbn: String, title: String) =
        BookMetadata(isbn = isbn, title = title, author = null)
}

private class RecordingService(
    private val lookupResult: LookupResult = LookupResult.NotFound,
    private val searchResult: SearchResult = SearchResult.Empty,
) : IsbnLookupService {
    var lookupCalls = 0
    var searchCalls = 0
    override suspend fun lookupByIsbn(isbn: String): LookupResult {
        lookupCalls++
        return lookupResult
    }
    override suspend fun searchByTitle(query: String, limit: Int): SearchResult {
        searchCalls++
        return searchResult
    }
}

private class FakeCache : IsbnMetadataCacheDao {
    private val map = mutableMapOf<String, IsbnMetadataCacheEntity>()
    override suspend fun get(isbn: String): IsbnMetadataCacheEntity? = map[isbn]
    override suspend fun upsert(entry: IsbnMetadataCacheEntity) { map[entry.isbn] = entry }
    override suspend fun delete(isbn: String) { map.remove(isbn) }
    override suspend fun deleteOlderThan(olderThan: Long): Int {
        val before = map.size
        map.entries.removeAll { it.value.fetchedAt < olderThan }
        return before - map.size
    }
    override suspend fun count(): Int = map.size
}
