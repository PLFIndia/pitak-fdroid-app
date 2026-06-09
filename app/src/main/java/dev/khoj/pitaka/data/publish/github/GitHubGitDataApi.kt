package dev.khoj.pitaka.data.publish.github

import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * GitHub **Git Data API** (low-level git plumbing) for atomic, incremental
 * publishing — host `https://api.github.com/`.
 *
 * Why this instead of the Contents API (see [GitHubContentsApi]): the Contents
 * API does GET-sha + PUT per file and ONE commit per file, strictly serial, and
 * can leave the repo half-updated if a later file fails. The Git Data API lets
 * us build a SINGLE commit no matter how many files change:
 *
 *   1. [getRef]      — read the branch head commit sha (404 = empty repo).
 *   2. [createBlob]  — upload each CHANGED file's bytes, get a blob sha. Blob
 *                      creates don't move the ref, so they're parallel-safe.
 *   3. [createTree]  — new tree from `base_tree` (head's tree) + only the changed
 *                      entries; unchanged files are inherited by omission.
 *   4. [createCommit]— commit pointing at the new tree, parent = old head.
 *   5. [updateRef]   — move the branch to the new commit. Atomic: the page never
 *                      sees a partial update.
 *
 * The blob sha GitHub returns is the standard git object hash
 * `sha1("blob " + len + "\u0000" + bytes)`, which we also compute locally
 * (GitBlobSha) to diff against our manifest WITHOUT uploading — that is what
 * makes repeat publishes incremental.
 *
 * Also exposes the **Pages builds** status endpoint ([latestPagesBuild]) so the
 * UI can tell the user when the page is actually live (not merely uploaded).
 * Best-effort: a `public_repo`-scoped token may get 403/404 here; callers treat
 * that as "status unknown" and fall back to a generic "may take a minute" note.
 *
 * All endpoints take the bearer token via an explicit [Header] so an
 * unauthenticated call is impossible to make by accident.
 */
interface GitHubGitDataApi {

    // Repo metadata — used to resolve the Pages-serving (default) branch instead
    // of assuming "main". The old Contents API wrote to the default branch
    // implicitly; we preserve that by resolving it explicitly here.
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<RepoInfoDto>

    // --- 1. read branch head ---------------------------------------------------
    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<GitRefDto>

    // --- 2. create a blob (one changed file's bytes) ---------------------------
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateBlobRequest,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<CreateBlobResponse>

    // --- 3. create a tree ------------------------------------------------------
    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateTreeRequest,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<CreateTreeResponse>

    // --- 4. create a commit ----------------------------------------------------
    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateCommitRequest,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<CreateCommitResponse>

    // Resolve a commit's tree sha — needed as `base_tree` so unchanged files are
    // inherited by omission (we only list changed entries in the new tree).
    @GET("repos/{owner}/{repo}/git/commits/{commitSha}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("commitSha") commitSha: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<GetCommitResponse>

    // --- 5. move the branch ref ------------------------------------------------
    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: UpdateRefRequest,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<GitRefDto>

    // Create a branch ref — only for the empty-repo bootstrap (no head to move).
    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<GitRefDto>

    // --- read the full tree (fresh-install manifest rebuild fallback) ----------
    // recursive=1 flattens the whole tree so we get every blob path+sha in one
    // call to reconstruct a missing local manifest from the repo's real state.
    @GET("repos/{owner}/{repo}/git/trees/{treeSha}?recursive=1")
    suspend fun getTreeRecursive(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("treeSha") treeSha: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<TreeDto>

    // --- Pages build status (best-effort "is it live yet?") --------------------
    @GET("repos/{owner}/{repo}/pages/builds/latest")
    suspend fun latestPagesBuild(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = ACCEPT,
    ): Response<PagesBuildDto>

    companion object {
        const val ACCEPT = "application/vnd.github+json"
        /** Git file mode for a normal (non-executable) blob. */
        const val MODE_FILE = "100644"
    }
}

// --- DTOs --------------------------------------------------------------------

data class GitRefDto(
    @Json(name = "ref") val ref: String? = null,
    @Json(name = "object") val obj: GitRefObjectDto? = null,
)

data class RepoInfoDto(
    @Json(name = "default_branch") val defaultBranch: String? = null,
)

data class GitRefObjectDto(
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "type") val type: String? = null,
)

data class CreateBlobRequest(
    @Json(name = "content") val content: String,
    @Json(name = "encoding") val encoding: String = "base64",
)

data class CreateBlobResponse(
    @Json(name = "sha") val sha: String? = null,
)

data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String?,
    @Json(name = "tree") val tree: List<TreeEntryRequest>,
)

data class TreeEntryRequest(
    @Json(name = "path") val path: String,
    @Json(name = "mode") val mode: String = GitHubGitDataApi.MODE_FILE,
    @Json(name = "type") val type: String = "blob",
    @Json(name = "sha") val sha: String,
)

data class CreateTreeResponse(
    @Json(name = "sha") val sha: String? = null,
)

data class CreateCommitRequest(
    @Json(name = "message") val message: String,
    @Json(name = "tree") val tree: String,
    @Json(name = "parents") val parents: List<String>,
)

data class CreateCommitResponse(
    @Json(name = "sha") val sha: String? = null,
)

data class GetCommitResponse(
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "tree") val tree: CommitTreeRefDto? = null,
)

data class CommitTreeRefDto(
    @Json(name = "sha") val sha: String? = null,
)

data class CreateRefRequest(
    /** Full ref name, e.g. "refs/heads/main". */
    @Json(name = "ref") val ref: String,
    @Json(name = "sha") val sha: String,
)

data class UpdateRefRequest(
    @Json(name = "sha") val sha: String,
    /** false = fast-forward only; we always append a child commit so FF holds. */
    @Json(name = "force") val force: Boolean = false,
)

data class TreeDto(
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "tree") val tree: List<TreeEntryDto> = emptyList(),
    @Json(name = "truncated") val truncated: Boolean = false,
)

data class TreeEntryDto(
    @Json(name = "path") val path: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "sha") val sha: String? = null,
)

data class PagesBuildDto(
    /** "built" | "building" | "errored" | null. */
    @Json(name = "status") val status: String? = null,
    @Json(name = "error") val error: PagesBuildErrorDto? = null,
)

data class PagesBuildErrorDto(
    @Json(name = "message") val message: String? = null,
)
