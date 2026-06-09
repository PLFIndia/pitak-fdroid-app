package dev.khoj.pitaka.data.remote.openlibrary

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Library REST surface used by Pitaka.
 *
 * Endpoints (verified against live responses, 2026-05):
 *
 * 1. ISBN lookup
 *    GET https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data
 *    Returns a JSON object keyed by the bibkey string (e.g. "ISBN:9780140428445").
 *    Pitaka asks for a single ISBN per call to keep parsing trivial.
 *
 * 2. Title search
 *    GET https://openlibrary.org/search.json?title={q}&limit={n}
 *    Returns a `docs` array of search-result objects.
 *
 * No authentication. Polite request rate (< 100/min unauthenticated). §1.1
 * compliant — public service, user-owned access.
 */
interface OpenLibraryApi {

    /**
     * Returns a map keyed by `bibkey` (e.g. `"ISBN:9780140428445"`). The value
     * is the record object; an empty map means "no hit." Moshi will decode an
     * empty response body as an empty map.
     */
    @GET("api/books")
    suspend fun books(
        @Query("bibkeys") bibkeys: String,
        @Query("format") format: String = "json",
        @Query("jscmd") jscmd: String = "data",
    ): Map<String, OpenLibraryBookDto>

    @GET("search.json")
    suspend fun searchByTitle(
        @Query("title") title: String,
        @Query("limit") limit: Int = 20,
    ): OpenLibrarySearchResponse
}

// --- ISBN-lookup DTOs ---

data class OpenLibraryBookDto(
    @Json(name = "title")              val title: String? = null,
    @Json(name = "subtitle")           val subtitle: String? = null,
    @Json(name = "authors")            val authors: List<OpenLibraryAuthorDto>? = null,
    @Json(name = "publishers")         val publishers: List<OpenLibraryNameDto>? = null,
    @Json(name = "publish_date")       val publishDate: String? = null,
    @Json(name = "number_of_pages")    val numberOfPages: Int? = null,
    @Json(name = "cover")              val cover: OpenLibraryCoverDto? = null,
    @Json(name = "subjects")           val subjects: List<OpenLibraryNameDto>? = null,
)

data class OpenLibraryAuthorDto(
    @Json(name = "name") val name: String? = null,
)

data class OpenLibraryNameDto(
    @Json(name = "name") val name: String? = null,
)

data class OpenLibraryCoverDto(
    @Json(name = "small")  val small: String? = null,
    @Json(name = "medium") val medium: String? = null,
    @Json(name = "large")  val large: String? = null,
)

// --- Title-search DTOs ---

data class OpenLibrarySearchResponse(
    @Json(name = "numFound") val numFound: Int? = null,
    @Json(name = "docs")     val docs: List<OpenLibrarySearchDoc>? = null,
)

data class OpenLibrarySearchDoc(
    @Json(name = "key")                  val key: String? = null,
    @Json(name = "title")                val title: String? = null,
    @Json(name = "author_name")          val authorName: List<String>? = null,
    @Json(name = "first_publish_year")   val firstPublishYear: Int? = null,
    @Json(name = "isbn")                 val isbn: List<String>? = null,
    @Json(name = "cover_i")              val coverId: Long? = null,
)
