package dev.khoj.pitaka.data.remote.openlibrary

import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.TitleSearchResult
import java.io.IOException
import javax.inject.Inject

/**
 * [IsbnLookupService] implementation backed by Open Library.
 *
 * Graceful-degradation rules (§1.1):
 *  - any IOException → [LookupResult.NetworkError] / [SearchResult.NetworkError].
 *  - empty response body or missing fields → [LookupResult.NotFound] /
 *    [SearchResult.Empty].
 *  - never throws to the caller. Domain-level callers never need a try/catch.
 */
class OpenLibraryLookupService @Inject constructor(
    private val api: OpenLibraryApi,
) : IsbnLookupService {

    override suspend fun lookupByIsbn(isbn: String): LookupResult = try {
        val bibkey = "ISBN:$isbn"
        val response = api.books(bibkeys = bibkey)
        val dto = response[bibkey]
        if (dto == null) LookupResult.NotFound else LookupResult.Found(dto.toBookMetadata(isbn))
    } catch (e: IOException) {
        LookupResult.NetworkError(e)
    } catch (e: retrofit2.HttpException) {
        // 4xx/5xx — treat as NotFound for 404, otherwise propagate as NetworkError.
        if (e.code() == 404) LookupResult.NotFound else LookupResult.NetworkError(e)
    }

    override suspend fun searchByTitle(query: String, limit: Int): SearchResult = try {
        val response = api.searchByTitle(title = query, limit = limit)
        val docs = response.docs.orEmpty().mapNotNull { it.toDomain() }
        if (docs.isEmpty()) SearchResult.Empty else SearchResult.Found(docs)
    } catch (e: IOException) {
        SearchResult.NetworkError(e)
    } catch (e: retrofit2.HttpException) {
        SearchResult.NetworkError(e)
    }
}

// --- Mapping ---

internal fun OpenLibraryBookDto.toBookMetadata(isbn: String): BookMetadata = BookMetadata(
    isbn = isbn,
    title = combineTitle(title, subtitle),
    author = authors?.mapNotNull { it.name }?.joinToString(", ")?.ifBlank { null },
    publisher = publishers?.firstOrNull { !it.name.isNullOrBlank() }?.name,
    publishedYear = publishDate?.let(::extractYear),
    pageCount = numberOfPages,
    coverUrl = cover?.medium ?: cover?.large ?: cover?.small,
    genre = subjects
        ?.mapNotNull { it.name }
        ?.take(3)
        ?.joinToString(", ")
        ?.ifBlank { null },
    language = null, // OL exposes "languages" but only as a list of `/languages/eng` style refs.
)

internal fun OpenLibrarySearchDoc.toDomain(): TitleSearchResult? {
    val realTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val sourceKey = key ?: realTitle
    return TitleSearchResult(
        sourceKey = sourceKey,
        title = realTitle,
        author = authorName?.firstOrNull()?.takeIf { it.isNotBlank() },
        publishedYear = firstPublishYear,
        isbn = isbn?.firstOrNull { it.length == 13 || it.length == 10 },
        coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
    )
}

private fun combineTitle(title: String?, subtitle: String?): String? {
    val t = title?.trim().orEmpty()
    val s = subtitle?.trim().orEmpty()
    return when {
        t.isNotEmpty() && s.isNotEmpty() -> "$t: $s"
        t.isNotEmpty() -> t
        s.isNotEmpty() -> s
        else -> null
    }
}

/** Open Library returns publish_date as a free-form string ("2003", "March 17, 2003", etc). */
private val YEAR_REGEX = Regex("""\b(1[5-9]\d\d|20\d\d|21\d\d)\b""")
internal fun extractYear(s: String): Int? = YEAR_REGEX.find(s)?.value?.toIntOrNull()
