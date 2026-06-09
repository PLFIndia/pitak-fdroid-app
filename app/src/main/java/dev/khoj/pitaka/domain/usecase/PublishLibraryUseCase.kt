package dev.khoj.pitaka.domain.usecase

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.images.CoverPaths
import dev.khoj.pitaka.data.images.ImagePipeline
import dev.khoj.pitaka.data.publish.GitBlobSha
import dev.khoj.pitaka.data.publish.PublishBook
import dev.khoj.pitaka.data.publish.PublishCoverIds
import dev.khoj.pitaka.data.publish.PublishExport
import dev.khoj.pitaka.data.publish.PublishManifest
import dev.khoj.pitaka.data.publish.PublishManifestStore
import dev.khoj.pitaka.data.publish.github.GitHubCredentialStore
import dev.khoj.pitaka.data.publish.github.GitHubGitDataApi
import dev.khoj.pitaka.data.publish.github.GitTreePublisher
import dev.khoj.pitaka.domain.repository.BookRepository
import dev.khoj.pitaka.domain.repository.LoanRepository
import dev.khoj.pitaka.data.vault.VaultLockedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Base64
import javax.inject.Inject

/**
 * Builds the publishable bundle (books.json + index.html + covers/ + inline
 * logo) and pushes it to the user's GitHub repo — now via the **Git Data API**
 * as a single atomic commit, and **incrementally** (only changed files are
 * fetched/downscaled/uploaded).
 *
 * Privacy (unchanged): every world-facing record goes through [redactForPublish]
 * (strips id/notes/location/source/addedDate/needsMetadata). Cover filenames are
 * salted via [PublishCoverIds]. Coarse availability (D31/D32) is computed only
 * when the vault is unlocked.
 *
 * Performance rework (PLAN-publish.md):
 *  - **Atomic upload:** blobs → tree(base_tree) → commit → move ref. One commit,
 *    so the page never shows a half-update and there's no reason to publish twice.
 *  - **Incremental:** a [PublishManifest] (filesDir JSON) records each file's git
 *    blob sha + each book's last-published coverUrl. Unchanged files are sent to
 *    the tree by their existing sha WITHOUT re-uploading. Q2-C cover rule: local
 *    covers are content-hashed; remote covers use source-identity skip.
 *  - **Robustness:** if a remote cover re-fetch fails but the cover already
 *    exists in the repo (manifest sha), we REUSE it instead of dropping it —
 *    directly fixing "covers sometimes vanish from the page."
 *  - **Progress:** emits coarse [Phase]s via [onPhase] so the UI can show what's
 *    happening instead of a blank spinner.
 */
class PublishLibraryUseCase(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
    private val loans: LoanRepository,
    private val prefs: dev.khoj.pitaka.data.prefs.AppPreferences,
    private val moshi: Moshi,
    private val publisher: GitTreePublisher,
    private val gitDataApi: GitHubGitDataApi,
    private val credentials: GitHubCredentialStore,
    private val manifestStore: PublishManifestStore,
    private val imagePipeline: ImagePipeline,
    private val coverIds: PublishCoverIds,
    private val clock: () -> Long,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        books: BookRepository,
        loans: LoanRepository,
        prefs: dev.khoj.pitaka.data.prefs.AppPreferences,
        moshi: Moshi,
        publisher: GitTreePublisher,
        gitDataApi: GitHubGitDataApi,
        credentials: GitHubCredentialStore,
        manifestStore: PublishManifestStore,
        imagePipeline: ImagePipeline,
        coverIds: PublishCoverIds,
        httpClient: OkHttpClient,
    ) : this(
        context, books, loans, prefs, moshi, publisher, gitDataApi, credentials,
        manifestStore, imagePipeline, coverIds,
        clock = System::currentTimeMillis,
        httpClient = httpClient,
    )

    /** Coarse publish phases for the UI (progress option B). */
    enum class Phase {
        PREPARING,       // building books.json / viewer, deciding what changed
        UPLOADING,       // creating blobs for changed files
        COMMITTING,      // tree + commit + ref move
        PAGES_BUILDING,  // GitHub is building the page (best-effort, P6)
    }

    sealed interface Result {
        data class Success(
            val pagesUrl: String?,
            val files: List<String>,
            val availabilityOmitted: Boolean = false,
            /** null = build status unknown (e.g. token lacks Pages scope). */
            val pagesLive: Boolean? = null,
        ) : Result
        data class Failed(val reason: String) : Result
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend operator fun invoke(onPhase: (Phase) -> Unit = {}): Result {
        if (credentials.currentToken() == null) return Result.Failed("Not signed in to GitHub.")
        val ownerSlashRepo = credentials.targetRepo()
            ?: return Result.Failed("Pick a target repo first.")
        val (owner, repo) = ownerSlashRepo.split("/", limit = 2)
            .takeIf { it.size == 2 } ?: return Result.Failed("Pick a target repo first.")
        val auth = "Bearer ${credentials.currentToken()}"

        onPhase(Phase.PREPARING)
        val now = clock()
        // Soft-deleted books (PLAN-merge.md) are stripped from publish entirely:
        // a public lending catalogue must not advertise books that left the
        // collection. Filtering here is the single chokepoint — books.json,
        // cover decisions, and availability all derive from `library`, so a
        // removed book is absent from every published artifact.
        val library = books.observeAll().first().filterNot { it.removed }

        // Resolve the Pages-serving (default) branch; fall back to "main".
        val branch = runCatching {
            val r = gitDataApi.getRepo(owner, repo, auth)
            if (r.isSuccessful) r.body()?.defaultBranch else null
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "main"

        // Load the incremental manifest. If it's empty or for a different repo
        // (e.g. fresh install after a wipe), rebuild it from the repo's real git
        // tree so we don't blindly re-upload covers that already exist.
        var manifest = manifestStore.load()
        if (manifest.repo != ownerSlashRepo || manifest.fileShas.isEmpty()) {
            manifest = rebuildManifestFromRepo(owner, repo, branch, auth, ownerSlashRepo)
        }

        // --- Availability (D31/D32, vault-gated) ---
        var availabilityOmitted = false
        val availabilityByBookId: Map<Long, String> = try {
            library.associate { book ->
                val active = loans.getAllForBook(book.id).count { it.returnedDate == null }
                val status = if (active >= book.copyCount) PublishBook.OUT else PublishBook.AVAILABLE
                book.id to status
            }
        } catch (e: VaultLockedException) {
            availabilityOmitted = true
            emptyMap()
        }

        // --- Covers (incremental, Q2-C) ---
        // Decide per book whether its cover needs (re)upload, and produce the
        // published coverUrl for books.json. Remote fetches run bounded-parallel.
        val coverDecisions = decideCovers(manifest, library)

        // --- books.json (always rebuilt; redaction + coarse availability) ---
        val publishBooks: List<PublishBook> = library.map { book ->
            val decision = coverDecisions[book.id]
            val availability = availabilityByBookId[book.id]
            val redacted = redactForPublish(book) { b ->
                decision?.publishedCoverUrl ?: CoverUrlAllowList.sanitizeCoverUrl(b.coverUrl)
            }
            PublishBook.fromRedacted(redacted, availability)
        }
        val export = PublishExport(PublishExport.SCHEMA_VERSION, now, publishBooks)
        val booksJson = moshi.adapter<PublishExport>().indent("  ").toJson(export).toByteArray(Charsets.UTF_8)

        // --- index.html (name + logo + contact) ---
        val viewerTemplate = runCatching {
            context.assets.open("publish/index.html").use { it.readBytes() }
        }.getOrNull() ?: return Result.Failed("Bundled viewer asset missing")
        val libraryName = prefs.libraryName().first().ifBlank { "My Library" }
        val logoDataUrl = readLogoDataUrl()
        val contactHtml = PublishContactLinks.render(
            PublishContactLinks.Contact(
                location = prefs.publishContactLocation().first(),
                email = prefs.publishContactEmail().first(),
                phone = prefs.publishContactPhone().first(),
            ),
            escape = ::htmlEscape,
        )
        val viewerHtml = String(viewerTemplate, Charsets.UTF_8)
            .replace("{{LIBRARY_NAME}}", htmlEscape(libraryName))
            .replace("{{LOGO_DATA_URL}}", logoDataUrl)
            .replace("{{CONTACT_HTML}}", contactHtml)
            .toByteArray(Charsets.UTF_8)

        // --- Assemble the desired file set with per-file change detection ---
        val desired = mutableListOf<GitTreePublisher.DesiredFile>()
        desired += desiredFile("books.json", booksJson, manifest)
        desired += desiredFile("index.html", viewerHtml, manifest)
        for (d in coverDecisions.values) {
            if (d.coverFileBytes != null) {
                // We have bytes (fetched/downscaled or read locally): upload iff sha changed.
                desired += GitTreePublisher.DesiredFile(
                    path = d.publishPath!!,
                    bytes = d.coverFileBytes,
                    gitSha = d.gitSha!!,
                    upload = manifest.shaFor(d.publishPath) != d.gitSha,
                )
            } else if (d.reuseSha != null) {
                // No bytes but the cover already exists in the repo → reuse it.
                desired += GitTreePublisher.DesiredFile(
                    path = d.publishPath!!,
                    bytes = ByteArray(0),
                    gitSha = d.reuseSha,
                    upload = false,
                )
            }
            // else: no cover for this book — nothing in the tree.
        }

        // De-dup by path (two books could hash-collide a salted path only in
        // astronomically unlikely cases; keep the first deterministically).
        val deduped = desired.distinctBy { it.path }

        onPhase(if (deduped.any { it.upload }) Phase.UPLOADING else Phase.COMMITTING)

        val pubResult = publisher.publish(
            files = deduped,
            branch = branch,
            commitMessage = "Pitaka publish $now",
        )

        return when (pubResult) {
            is GitTreePublisher.Result.Success -> {
                // Persist the new manifest from what we just published.
                val newFileShas = deduped.associate { it.path to it.gitSha }
                val newCoverUrls = library
                    .filter { it.coverUrl?.isNotBlank() == true }
                    .associate { it.id.toString() to it.coverUrl!! }
                manifestStore.save(
                    PublishManifest(
                        repo = ownerSlashRepo,
                        fileShas = newFileShas,
                        coverUrlByBookId = newCoverUrls,
                    )
                )
                onPhase(Phase.PAGES_BUILDING)
                val pagesLive = pollPagesBuild(owner, repo, auth)
                Result.Success(
                    pagesUrl = "https://$owner.github.io/$repo/",
                    files = pubResult.uploadedPaths,
                    availabilityOmitted = availabilityOmitted,
                    pagesLive = pagesLive,
                )
            }
            GitTreePublisher.Result.NotAuthenticated -> Result.Failed("Not signed in to GitHub.")
            GitTreePublisher.Result.NoTargetRepo -> Result.Failed("Pick a target repo first.")
            is GitTreePublisher.Result.HttpError -> Result.Failed("GitHub HTTP ${pubResult.code}: ${pubResult.body.take(200)}")
            is GitTreePublisher.Result.NetworkError -> Result.Failed("Network error: ${pubResult.cause.message ?: "offline"}")
        }
    }

    // --- cover decision ------------------------------------------------------

    private class CoverDecision(
        val publishPath: String?,
        /** Bytes to upload (already downscaled), or null when reusing/none. */
        val coverFileBytes: ByteArray?,
        val gitSha: String?,
        /** Existing repo sha to reuse when we have no fresh bytes. */
        val reuseSha: String?,
        /** What books.json should point at: the publish path, a sanitized remote URL, or null. */
        val publishedCoverUrl: String?,
    )

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun decideCovers(
        manifest: PublishManifest,
        library: List<dev.khoj.pitaka.domain.model.Book>,
    ): Map<Long, CoverDecision> = coroutineScope {
        val gate = Semaphore(COVER_FETCH_PARALLELISM)
        library.map { book ->
            async(Dispatchers.IO) {
                book.id to decideOneCover(manifest, book, gate)
            }
        }.awaitAll().toMap()
    }

    private suspend fun decideOneCover(
        manifest: PublishManifest,
        book: dev.khoj.pitaka.domain.model.Book,
        gate: Semaphore,
    ): CoverDecision {
        val src = book.coverUrl?.takeIf { it.isNotBlank() }
            ?: return CoverDecision(null, null, null, null, null)
        val publishPath = coverIds.pathFor(book.id)
        val repoSha = manifest.shaFor(publishPath)

        // LOCAL cover: hash locally (cheap, exact). Always re-derive bytes so a
        // user-edited cover is detected by sha change.
        if (CoverPaths.isLocal(src)) {
            val bytes = readLocalCover(src)
            val shrunk = bytes?.let { imagePipeline.downscaleForPublish(it.inputStream()) }
            return if (shrunk != null) {
                CoverDecision(publishPath, shrunk, GitBlobSha.of(shrunk), null, publishPath)
            } else if (repoSha != null) {
                // Local file vanished but cover is in the repo → keep it.
                CoverDecision(publishPath, null, null, repoSha, publishPath)
            } else {
                CoverDecision(null, null, null, null, null)
            }
        }

        // REMOTE cover: Q2-C source-identity skip.
        val urlUnchanged = manifest.coverUrlByBookId[book.id.toString()] == src
        if (urlUnchanged && repoSha != null) {
            // URL unchanged and cover already in repo → skip fetch entirely.
            return CoverDecision(publishPath, null, null, repoSha, publishPath)
        }
        // Fetch + downscale (bounded-parallel via the gate).
        val shrunk = gate.withPermit {
            val bytes = readRemoteCover(src)
            bytes?.let { imagePipeline.downscaleForPublish(it.inputStream()) }
        }
        return when {
            shrunk != null ->
                CoverDecision(publishPath, shrunk, GitBlobSha.of(shrunk), null, publishPath)
            repoSha != null ->
                // Fetch failed but cover already in repo → REUSE (robustness fix).
                CoverDecision(publishPath, null, null, repoSha, publishPath)
            else ->
                // No usable cover: fall back to a sanitized remote URL or null.
                CoverDecision(null, null, null, null, CoverUrlAllowList.sanitizeCoverUrl(src))
        }
    }

    // --- manifest rebuild from repo (fresh-install safety net) ---------------

    private suspend fun rebuildManifestFromRepo(
        owner: String, repo: String, branch: String, auth: String, ownerSlashRepo: String,
    ): PublishManifest {
        return runCatching {
            val ref = gitDataApi.getRef(owner, repo, branch, auth)
            if (!ref.isSuccessful) return PublishManifest.EMPTY
            val headSha = ref.body()?.obj?.sha ?: return PublishManifest.EMPTY
            val commit = gitDataApi.getCommit(owner, repo, headSha, auth)
            val treeSha = commit.body()?.tree?.sha ?: return PublishManifest.EMPTY
            val tree = gitDataApi.getTreeRecursive(owner, repo, treeSha, auth)
            if (!tree.isSuccessful) return PublishManifest.EMPTY
            val shas = tree.body()?.tree.orEmpty()
                .filter { it.type == "blob" && it.path != null && it.sha != null }
                .associate { it.path!! to it.sha!! }
            PublishManifest(repo = ownerSlashRepo, fileShas = shas, coverUrlByBookId = emptyMap())
        }.getOrDefault(PublishManifest.EMPTY)
    }

    // --- Pages build poll (best-effort, P6) ----------------------------------
    // Returns true=built, false=errored, null=unknown (timeout or no Pages scope).
    private suspend fun pollPagesBuild(owner: String, repo: String, auth: String): Boolean? {
        val deadline = System.currentTimeMillis() + PAGES_POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching {
                val r = gitDataApi.latestPagesBuild(owner, repo, auth)
                if (r.isSuccessful) r.body()?.status else null // 403/404 → null (no scope)
            }.getOrNull()
            when (status) {
                "built" -> return true
                "errored" -> return false
                null -> return null // can't read status; stop polling
                else -> withContext(Dispatchers.IO) { Thread.sleep(PAGES_POLL_INTERVAL_MS) }
            }
        }
        return null // still building at timeout → "may take a minute"
    }

    private fun desiredFile(path: String, bytes: ByteArray, manifest: PublishManifest): GitTreePublisher.DesiredFile {
        val sha = GitBlobSha.of(bytes)
        return GitTreePublisher.DesiredFile(path, bytes, sha, upload = manifest.shaFor(path) != sha)
    }

    private fun readLocalCover(src: String): ByteArray? = runCatching {
        val file = CoverPaths.absoluteCoverFile(context.filesDir, src) ?: return null
        if (!file.exists()) return null
        file.readBytes()
    }.getOrNull()

    private fun readRemoteCover(src: String): ByteArray? = runCatching {
        val req = Request.Builder().url(src).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.bytes()
        }
    }.getOrNull()

    private suspend fun readLogoDataUrl(): String {
        val uriStr = prefs.libraryLogoUri().first()
        if (uriStr.isBlank()) return ""
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return ""
        val path = uri.path ?: return ""
        val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return ""
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:image/png;base64,$encoded"
    }

    private fun htmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    companion object {
        const val COVER_FETCH_PARALLELISM = 6
        const val PAGES_POLL_TIMEOUT_MS = 60_000L
        const val PAGES_POLL_INTERVAL_MS = 3_000L
    }
}
