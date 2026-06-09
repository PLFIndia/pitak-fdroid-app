package dev.khoj.pitaka.data.remote.googlebooks

import dev.khoj.pitaka.data.remote.openlibrary.extractYear
import dev.khoj.pitaka.domain.lookup.IsbnLookupService
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import dev.khoj.pitaka.domain.model.BookMetadata
import dev.khoj.pitaka.domain.model.TitleSearchResult
import java.io.IOException
import javax.inject.Inject

class GoogleBooksLookupService @Inject constructor(
    private val api: GoogleBooksApi,
) : IsbnLookupService {

    override suspend fun lookupByIsbn(isbn: String): LookupResult = try {
        val response = api.search(query = "isbn:$isbn", maxResults = 1)
        val first = response.items.orEmpty().firstOrNull()
        val info = first?.volumeInfo
        if (info == null) LookupResult.NotFound else LookupResult.Found(info.toBookMetadata(isbn))
    } catch (e: IOException) {
        LookupResult.NetworkError(e)
    } catch (e: retrofit2.HttpException) {
        if (e.code() == 404) LookupResult.NotFound else LookupResult.NetworkError(e)
    }

    override suspend fun searchByTitle(query: String, limit: Int): SearchResult = try {
        val response = api.search(query = "intitle:\"$query\"", maxResults = limit)
        val docs = response.items.orEmpty().mapNotNull { it.toDomain() }
        if (docs.isEmpty()) SearchResult.Empty else SearchResult.Found(docs)
    } catch (e: IOException) {
        SearchResult.NetworkError(e)
    } catch (e: retrofit2.HttpException) {
        SearchResult.NetworkError(e)
    }
}

// --- Mapping ---

internal fun GoogleBooksVolumeInfo.toBookMetadata(isbn: String): BookMetadata = BookMetadata(
    isbn = isbn,
    title = combineTitle(title, subtitle),
    author = authors?.joinToString(", ")?.ifBlank { null },
    publisher = publisher,
    publishedYear = publishedDate?.let(::extractYear),
    pageCount = pageCount,
    coverUrl = imageLinks?.thumbnail ?: imageLinks?.smallThumbnail,
    genre = categories?.take(3)?.joinToString(", ")?.ifBlank { null },
    language = language,
)

internal fun GoogleBooksItem.toDomain(): TitleSearchResult? {
    val info = volumeInfo ?: return null
    val realTitle = info.title?.takeIf { it.isNotBlank() } ?: return null
    val isbn13 = info.identifiers
        ?.firstOrNull { it.type == "ISBN_13" }
        ?.identifier
    val isbn10 = info.identifiers
        ?.firstOrNull { it.type == "ISBN_10" }
        ?.identifier
    return TitleSearchResult(
        sourceKey = id ?: realTitle,
        title = realTitle,
        author = info.authors?.firstOrNull()?.takeIf { it.isNotBlank() },
        publishedYear = info.publishedDate?.let(::extractYear),
        isbn = isbn13 ?: isbn10,
        coverUrl = info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail,
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
