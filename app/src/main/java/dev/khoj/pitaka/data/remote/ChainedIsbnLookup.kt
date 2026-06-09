package dev.khoj.pitaka.data.remote

import com.squareup.moshi.Moshi
import dev.khoj.pitaka.data.local.cache.CacheDatabase
import dev.khoj.pitaka.data.local.cache.IsbnMetadataCacheDao
import dev.khoj.pitaka.data.local.cache.IsbnMetadataCacheEntity
import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.IsbnFormat
import dev.khoj.pitaka.domain.model.TitleSearchResult
import javax.inject.Inject
import javax.inject.Named

/**
 * Composer that runs ISBN lookups against a cache, then a primary, then a
 * fallback service in order (pitaka.md §5.2 pattern).
 *
 * Behaviour:
 *  - ISBN lookup
 *      1. Cache hit (within TTL) → return Found from cache.
 *         A cached NotFound sentinel (source = [SOURCE_NOT_FOUND], shorter
 *         TTL = [NOT_FOUND_TTL_MS]) short-circuits to NotFound — saves
 *         re-querying every provider for a known-missing ISBN.
 *      2. Primary `Found` → write through to cache, return.
 *      3. Primary `NotFound`     → try fallback.
 *      4. Primary `NetworkError` → try fallback (network can still be partial).
 *      5. Any provider `Found`   → write through to cache, return.
 *      6. All providers `NotFound` → cache the NotFound sentinel, return
 *         NotFound. Does NOT cache when the chain saw a NetworkError —
 *         those are transient and the user might be offline.
 *      7. All providers errored  → return NetworkError (the first error seen).
 *
 *  - Title search
 *      Runs against the primary; if NetworkError, tries the fallback.
 *      Results are deduplicated by ISBN when present, keeping the first one.
 *
 * The cache is keyed by ISBN. Title-search results aren't cached — TTL on a
 * search result is hard to reason about and the value of caching a search
 * shape is low.
 *
 * Construction note: `clock` is non-injected because Dagger has no binding for
 * `() -> Long`. Tests override it via the secondary constructor; production
 * paths use [System.currentTimeMillis].
 */
class ChainedIsbnLookup(
    private val primary: IsbnLookupService,
    private val fallback: IsbnLookupService,
    private val cacheDao: IsbnMetadataCacheDao,
    private val moshi: Moshi,
    private val clock: () -> Long,
    // F-14 (audit): reuse the singleton OkHttpClient instead of
    // allocating a new one per cover probe. Default keeps the existing
    // test call sites — which pass positional args up to `clock` —
    // working without a sweep, while production wires the singleton
    // through the @Inject constructor below.
    private val httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient(),
) : IsbnLookupService {

    @Inject
    constructor(
        @Named(PRIMARY) primary: IsbnLookupService,
        @Named(FALLBACK) fallback: IsbnLookupService,
        cacheDao: IsbnMetadataCacheDao,
        moshi: Moshi,
        httpClient: okhttp3.OkHttpClient,
    ) : this(
        primary = primary,
        fallback = fallback,
        cacheDao = cacheDao,
        moshi = moshi,
        clock = System::currentTimeMillis,
        httpClient = httpClient,
    )

    private val metadataAdapter = moshi.adapter(BookMetadata::class.java)

    override suspend fun lookupByIsbn(isbn: String): LookupResult {
        val now = clock()
        val cached = cacheDao.get(isbn)
        if (cached != null) {
            if (cached.source == SOURCE_NOT_FOUND) {
                if (now - cached.fetchedAt < NOT_FOUND_TTL_MS) {
                    // We asked every provider recently and they all said no.
                    // Don't bother re-asking until the 24h sentinel expires.
                    return LookupResult.NotFound
                }
                // Stale NotFound — fall through and re-query.
            } else if (now - cached.fetchedAt < CacheDatabase.ISBN_METADATA_TTL_MS) {
                val parsed = runCatching { metadataAdapter.fromJson(cached.payload) }.getOrNull()
                if (parsed != null) return LookupResult.Found(parsed)
            }
        }

        var firstError: Throwable? = null

        when (val r = primary.lookupByIsbn(isbn)) {
            is LookupResult.Found -> {
                val enriched = ensureCover(r.metadata)
                writeThrough(enriched, source = "primary")
                return LookupResult.Found(enriched)
            }
            is LookupResult.NotFound -> Unit
            is LookupResult.NetworkError -> firstError = r.cause
        }

        when (val r = fallback.lookupByIsbn(isbn)) {
            is LookupResult.Found -> {
                val enriched = ensureCover(r.metadata)
                writeThrough(enriched, source = "fallback")
                return LookupResult.Found(enriched)
            }
            is LookupResult.NotFound -> {
                // Both providers responded definitively that they don't know.
                // Cache the NotFound only if no transient errors happened;
                // otherwise the user might be offline and the books may
                // actually exist.
                return if (firstError != null) {
                    LookupResult.NetworkError(firstError)
                } else {
                    writeNotFound(isbn)
                    LookupResult.NotFound
                }
            }
            is LookupResult.NetworkError -> {
                return LookupResult.NetworkError(firstError ?: r.cause)
            }
        }
    }

    /**
     * Phase 8: if the metadata has no cover URL, probe Open Library's
     * ISBN CDN (covers.openlibrary.org/b/isbn/<isbn>-L.jpg). The CDN
     * returns a 43-byte 1×1 GIF as its "no cover" sentinel, so we
     * HEAD-check Content-Length and only treat sizes > 200 as a real
     * cover. Safe to call: it's just one extra HEAD on metadata that
     * lacked a cover anyway, and gets cached with the metadata.
     */
    private fun ensureCover(metadata: BookMetadata): BookMetadata {
        if (!metadata.coverUrl.isNullOrBlank()) return metadata
        val isbn = metadata.isbn.takeIf { it.isNotBlank() } ?: return metadata
        // F-16: only probe the Open Library cover CDN for structurally valid
        // ISBNs. A typo'd or non-ISBN string can't escape the authority (OkHttp
        // canonicalises) but firing a doomed HEAD for it is wasted bandwidth.
        if (!IsbnFormat.isValid(isbn)) return metadata
        val url = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
        val realSize = runCatching { coverProbeSize(url) }.getOrNull() ?: return metadata
        if (realSize < OPEN_LIBRARY_PLACEHOLDER_THRESHOLD) return metadata
        return metadata.copy(coverUrl = url)
    }

    private fun coverProbeSize(url: String): Long? {
        val req = okhttp3.Request.Builder().url(url).head().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.contentLength()
                ?: resp.header("Content-Length")?.toLongOrNull()
        }
    }

    override suspend fun searchByTitle(query: String, limit: Int): SearchResult {
        val primaryResult = primary.searchByTitle(query, limit)
        return when (primaryResult) {
            is SearchResult.Found -> SearchResult.Found(primaryResult.results.dedupByIsbn())
            is SearchResult.Empty -> {
                // Sometimes the primary returns 0 hits but the fallback knows the
                // book. The D7 fallback chain says "try both before giving up."
                when (val fb = fallback.searchByTitle(query, limit)) {
                    is SearchResult.Found -> SearchResult.Found(fb.results.dedupByIsbn())
                    is SearchResult.Empty -> SearchResult.Empty
                    is SearchResult.NetworkError -> SearchResult.Empty // primary gave a clean Empty
                }
            }
            is SearchResult.NetworkError -> {
                when (val fb = fallback.searchByTitle(query, limit)) {
                    is SearchResult.Found -> SearchResult.Found(fb.results.dedupByIsbn())
                    is SearchResult.Empty -> SearchResult.NetworkError(primaryResult.cause)
                    is SearchResult.NetworkError -> SearchResult.NetworkError(primaryResult.cause ?: fb.cause)
                }
            }
        }
    }

    /** Force a fresh fetch, bypassing the cache. Used by the manual "Refresh metadata" action (D11). */
    suspend fun refresh(isbn: String): LookupResult {
        cacheDao.delete(isbn)
        return lookupByIsbn(isbn)
    }

    private suspend fun writeThrough(metadata: BookMetadata, source: String) {
        val payload = metadataAdapter.toJson(metadata)
        cacheDao.upsert(
            IsbnMetadataCacheEntity(
                isbn = metadata.isbn,
                payload = payload,
                source = source,
                fetchedAt = clock(),
            )
        )
    }

    /**
     * Cache that every provider responded NotFound for this ISBN. Stored
     * with [SOURCE_NOT_FOUND] as the source and an empty payload so the
     * existing TTL-keyed lookup short-circuits on the next scan.
     *
     * Reuses the same row as a Found entry would have used — a later Found
     * for the same ISBN will overwrite (REPLACE on conflict), so a book
     * that gets added to a provider after Pitak's first miss is recovered
     * automatically the next time the NotFound TTL expires.
     */
    private suspend fun writeNotFound(isbn: String) {
        cacheDao.upsert(
            IsbnMetadataCacheEntity(
                isbn = isbn,
                payload = "",
                source = SOURCE_NOT_FOUND,
                fetchedAt = clock(),
            )
        )
    }

    private fun List<TitleSearchResult>.dedupByIsbn(): List<TitleSearchResult> {
        val seen = mutableSetOf<String>()
        return buildList {
            this@dedupByIsbn.forEach { result ->
                val key = result.isbn
                if (key == null || seen.add(key)) add(result)
            }
        }
    }

    companion object {
        const val PRIMARY = "primary"
        const val FALLBACK = "fallback"

        /** Sentinel source value for a cached "every provider said no" row. */
        const val SOURCE_NOT_FOUND = "not-found"

        /** TTL for cached NotFound rows. Short so books newly added to a
         *  provider get recovered within a day; long enough to spare every
         *  scan of the same missing ISBN from a 2-provider round-trip. */
        const val NOT_FOUND_TTL_MS: Long = 24L * 60 * 60 * 1000

        /**
         * Open Library's "no cover" sentinel is a 43-byte 1×1 GIF.
         * Real covers start in the kilobytes. 200 is a safe cutoff.
         */
        const val OPEN_LIBRARY_PLACEHOLDER_THRESHOLD = 200L
    }
}
