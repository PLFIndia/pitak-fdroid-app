package dev.khoj.pitaka.data.publish.github

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * GitHub REST v3 endpoints for the publish UI's account/repo resolution.
 *
 * File reads/writes moved to the Git Data API ([GitHubGitDataApi]) for atomic,
 * incremental publishing; this interface now only resolves the authenticated
 * user and lists repos for the target-repo picker.
 *
 * Auth is passed explicitly via [Header] so an unauthenticated call can't be
 * made by accident.
 */
interface GitHubContentsApi {

    @GET("user")
    suspend fun currentUser(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
    ): GitHubUserDto

    @GET("user/repos")
    suspend fun userRepos(
        @Header("Authorization") authorization: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        @Header("Accept") accept: String = "application/vnd.github+json",
    ): List<GitHubRepoDto>
}

data class GitHubUserDto(
    @Json(name = "login") val login: String? = null,
)

data class GitHubRepoDto(
    @Json(name = "name")      val name: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "private")   val isPrivate: Boolean? = null,
    @Json(name = "html_url")  val htmlUrl: String? = null,
)
