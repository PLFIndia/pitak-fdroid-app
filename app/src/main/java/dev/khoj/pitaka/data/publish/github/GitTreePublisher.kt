package dev.khoj.pitaka.data.publish.github

import dev.khoj.pitaka.data.publish.GitBlobSha
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Atomic, incremental uploader built on the GitHub **Git Data API**.
 *
 * Replaces the per-file Contents-API loop (GET sha + PUT, one commit per file,
 * serial, aborts mid-way) with a SINGLE commit no matter how many files change:
 *
 *   ref → blobs(changed only, bounded-parallel) → tree(base_tree + changed) →
 *   commit(parent=head) → move ref
 *
 * Properties:
 *  - **Atomic:** the branch ref moves exactly once, at the end. The published
 *    page never observes a half-updated state (fixes the "books.json references
 *    covers that weren't pushed yet" bug and removes the reason to "publish
 *    twice").
 *  - **Incremental:** the caller passes each file's locally-computed
 *    [DesiredFile.gitSha]; any file whose sha already matches what's in the repo
 *    (per the caller's manifest/tree diff) is sent with `upload = false` and
 *    contributes its existing sha to the new tree WITHOUT re-uploading bytes.
 *  - **Bounded-parallel blobs:** blob creates don't touch the ref, so they run
 *    concurrently under a small semaphore (default 6) — the main latency win.
 *
 * The whole operation is best-effort-atomic: any failure returns a typed
 * [Result] BEFORE the ref is moved, so a failed publish leaves the live page on
 * its previous commit, untouched.
 */
class GitTreePublisher @Inject constructor(
    private val api: GitHubGitDataApi,
    private val credentials: GitHubCredentialStore,
) {
    /**
     * One file we want present in the repo after this publish.
     * @param path repo-relative path (e.g. "books.json", "covers/3f2c.jpg").
     * @param bytes the content; needed only when [upload] is true.
     * @param gitSha the file's git blob sha (from [GitBlobSha.of]); always set,
     *   used as the tree entry sha when [upload] is false.
     * @param upload true = this file changed (or is new) and its bytes must be
     *   uploaded as a blob; false = unchanged, reuse [gitSha] in the tree.
     */
    data class DesiredFile(
        val path: String,
        val bytes: ByteArray,
        val gitSha: String,
        val upload: Boolean,
    )

    sealed interface Result {
        /** @param commitSha the new head; @param uploadedPaths files whose bytes were pushed. */
        data class Success(val commitSha: String, val uploadedPaths: List<String>) : Result
        data object NotAuthenticated : Result
        data object NoTargetRepo : Result
        data class HttpError(val code: Int, val body: String) : Result
        data class NetworkError(val cause: Throwable) : Result
    }

    /**
     * @param branch the Pages-serving branch (e.g. "main").
     * @param commitMessage message for the single commit.
     * @param parallelism max concurrent blob uploads.
     */
    suspend fun publish(
        files: List<DesiredFile>,
        branch: String,
        commitMessage: String,
        parallelism: Int = DEFAULT_PARALLELISM,
    ): Result {
        val token = credentials.currentToken() ?: return Result.NotAuthenticated
        val auth = "Bearer $token"
        val ownerSlashRepo = credentials.targetRepo() ?: return Result.NoTargetRepo
        val (owner, repo) = ownerSlashRepo.split("/", limit = 2)
            .takeIf { it.size == 2 } ?: return Result.NoTargetRepo

        return try {
            // 1. Current head + its tree (base_tree). 404/empty repo → bootstrap.
            val headSha: String? = run {
                val r = api.getRef(owner, repo, branch, auth)
                when {
                    r.isSuccessful -> r.body()?.obj?.sha
                    r.code() == 404 || r.code() == 409 -> null // empty repo
                    else -> return Result.HttpError(r.code(), r.errorBody()?.string().orEmpty())
                }
            }
            val baseTreeSha: String? = if (headSha != null) {
                val r = api.getCommit(owner, repo, headSha, auth)
                if (!r.isSuccessful) return Result.HttpError(r.code(), r.errorBody()?.string().orEmpty())
                r.body()?.tree?.sha
            } else null

            // 2. Upload changed blobs, bounded-parallel. Unchanged files skip this.
            val toUpload = files.filter { it.upload }
            val gate = Semaphore(parallelism.coerceAtLeast(1))
            val blobErr = java.util.concurrent.atomic.AtomicReference<Result>(null)
            coroutineScope {
                toUpload.map { f ->
                    async {
                        if (blobErr.get() != null) return@async
                        gate.withPermit {
                            val content = java.util.Base64.getEncoder().encodeToString(f.bytes)
                            val r = api.createBlob(owner, repo, CreateBlobRequest(content = content), auth)
                            if (!r.isSuccessful) {
                                blobErr.compareAndSet(null, Result.HttpError(r.code(), r.errorBody()?.string().orEmpty()))
                                return@async
                            }
                            // GitHub's returned blob sha MUST equal our local git
                            // sha; if not, our tree entry would be wrong. Trust
                            // the server value for the tree to be safe.
                            val serverSha = r.body()?.sha
                            if (serverSha != null && serverSha != f.gitSha) {
                                // Extremely unlikely (we verified the algorithm),
                                // but if it ever happens, use the server sha.
                                serverShaOverride[f.path] = serverSha
                            }
                        }
                    }
                }.awaitAll()
            }
            blobErr.get()?.let { return it }

            // 3. New tree: every desired file as an entry, by sha (uploaded or reused).
            val treeEntries = files.map { f ->
                TreeEntryRequest(path = f.path, sha = serverShaOverride[f.path] ?: f.gitSha)
            }
            val treeResp = api.createTree(
                owner, repo,
                CreateTreeRequest(baseTree = baseTreeSha, tree = treeEntries),
                auth,
            )
            if (!treeResp.isSuccessful) return Result.HttpError(treeResp.code(), treeResp.errorBody()?.string().orEmpty())
            val newTreeSha = treeResp.body()?.sha
                ?: return Result.HttpError(treeResp.code(), "Empty tree sha")

            // 4. Commit pointing at the new tree.
            val commitResp = api.createCommit(
                owner, repo,
                CreateCommitRequest(
                    message = commitMessage,
                    tree = newTreeSha,
                    parents = if (headSha != null) listOf(headSha) else emptyList(),
                ),
                auth,
            )
            if (!commitResp.isSuccessful) return Result.HttpError(commitResp.code(), commitResp.errorBody()?.string().orEmpty())
            val newCommitSha = commitResp.body()?.sha
                ?: return Result.HttpError(commitResp.code(), "Empty commit sha")

            // 5. Move (or create) the branch ref — the single atomic flip.
            val refResp = if (headSha != null) {
                api.updateRef(owner, repo, branch, UpdateRefRequest(sha = newCommitSha), auth)
            } else {
                api.createRef(owner, repo, CreateRefRequest(ref = "refs/heads/$branch", sha = newCommitSha), auth)
            }
            if (!refResp.isSuccessful) return Result.HttpError(refResp.code(), refResp.errorBody()?.string().orEmpty())

            Result.Success(commitSha = newCommitSha, uploadedPaths = toUpload.map { it.path })
        } catch (e: java.io.IOException) {
            Result.NetworkError(e)
        } finally {
            serverShaOverride.clear()
        }
    }

    // Rare-case map: server blob sha != our local sha (should never happen given
    // GitBlobShaTest, but if it did we must use the server's value in the tree).
    private val serverShaOverride = java.util.concurrent.ConcurrentHashMap<String, String>()

    companion object {
        const val DEFAULT_PARALLELISM = 6
    }
}
