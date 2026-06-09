package dev.khoj.pitaka.data.remote.googlebooks

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Books v1 surface used by Pitaka.
 *
 * Endpoints (verified live, 2026-05):
 *  - GET books/v1/volumes?q=isbn:{isbn}
 *  - GET books/v1/volumes?q=intitle:{q}&maxResults={n}
 *
 * No auth required for low-volume personal use. Quotas exist but are generous
 * enough that an individual user is rarely affected. §1.1 compliant — public
 * service, user-owned access.
 */
interface GoogleBooksApi {
    @GET("books/v1/volumes")
    suspend fun search(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 20,
    ): GoogleBooksResponse
}

data class GoogleBooksResponse(
    @Json(name = "totalItems") val totalItems: Int? = null,
    @Json(name = "items")      val items: List<GoogleBooksItem>? = null,
)

data class GoogleBooksItem(
    @Json(name = "id")          val id: String? = null,
    @Json(name = "volumeInfo")  val volumeInfo: GoogleBooksVolumeInfo? = null,
)

data class GoogleBooksVolumeInfo(
    @Json(name = "title")               val title: String? = null,
    @Json(name = "subtitle")            val subtitle: String? = null,
    @Json(name = "authors")             val authors: List<String>? = null,
    @Json(name = "publisher")           val publisher: String? = null,
    @Json(name = "publishedDate")       val publishedDate: String? = null,
    @Json(name = "pageCount")           val pageCount: Int? = null,
    @Json(name = "categories")          val categories: List<String>? = null,
    @Json(name = "language")            val language: String? = null,
    @Json(name = "industryIdentifiers") val identifiers: List<GoogleBooksIdentifier>? = null,
    @Json(name = "imageLinks")          val imageLinks: GoogleBooksImageLinks? = null,
)

data class GoogleBooksIdentifier(
    @Json(name = "type")       val type: String? = null,
    @Json(name = "identifier") val identifier: String? = null,
)

data class GoogleBooksImageLinks(
    @Json(name = "thumbnail")      val thumbnail: String? = null,
    @Json(name = "smallThumbnail") val smallThumbnail: String? = null,
)
