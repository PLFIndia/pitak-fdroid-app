package dev.khoj.pitaka.data.publish.github

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GitTreePublisherTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GitHubGitDataApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubGitDataApi::class.java)
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun creds(token: String? = "tok", repo: String? = "owner/repo") =
        mockk<GitHubCredentialStore>().also {
            every { it.currentToken() } returns token
            every { it.targetRepo() } returns repo
        }

    private fun file(path: String, body: String, upload: Boolean) =
        GitTreePublisher.DesiredFile(
            path = path,
            bytes = body.toByteArray(),
            gitSha = dev.khoj.pitaka.data.publish.GitBlobSha.of(body.toByteArray()),
            upload = upload,
        )

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun not_authenticated_when_token_missing() = runBlocking {
        val sut = GitTreePublisher(api, creds(token = null))
        val r = sut.publish(listOf(file("books.json", "{}", true)), "main", "msg")
        assertThat(r).isEqualTo(GitTreePublisher.Result.NotAuthenticated)
    }

    @Test
    fun happy_path_existing_repo_single_commit() = runBlocking {
        // getRef -> getCommit -> createBlob (x1 changed) -> createTree -> createCommit -> updateRef
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"HEAD1","type":"commit"}}"""))
        server.enqueue(json("""{"sha":"HEAD1","tree":{"sha":"BASETREE"}}"""))
        server.enqueue(json("""{"sha":"BLOBNEW"}""")) // createBlob for the changed file
        server.enqueue(json("""{"sha":"NEWTREE"}"""))
        server.enqueue(json("""{"sha":"NEWCOMMIT"}"""))
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"NEWCOMMIT"}}"""))

        val files = listOf(
            file("books.json", "{\"v\":2}", upload = true),  // changed
            file("covers/a.jpg", "AAAA", upload = false),    // unchanged, reuse sha
        )
        val sut = GitTreePublisher(api, creds())
        val r = sut.publish(files, "main", "Pitaka publish")

        assertThat(r).isInstanceOf(GitTreePublisher.Result.Success::class.java)
        val ok = r as GitTreePublisher.Result.Success
        assertThat(ok.commitSha).isEqualTo("NEWCOMMIT")
        // Only the changed file's bytes were uploaded.
        assertThat(ok.uploadedPaths).containsExactly("books.json")

        // Exactly one blob create happened (the unchanged cover was NOT uploaded).
        val paths = mutableListOf<String>()
        repeat(6) { paths += server.takeRequest().path!! }
        assertThat(paths.count { it.endsWith("/git/blobs") }).isEqualTo(1)
        // The tree request carries BOTH files (changed + reused), base_tree set.
        // (We already consumed requests; assert via count of tree/commit/ref.)
        assertThat(paths.count { it.endsWith("/git/trees") }).isEqualTo(1)
        assertThat(paths.count { it.endsWith("/git/commits") }).isEqualTo(1)
        assertThat(paths.any { it.contains("/git/refs/heads/main") }).isTrue()
    }

    @Test
    fun empty_repo_bootstraps_with_create_ref() = runBlocking {
        // getRef -> 404 (empty); no getCommit; createBlob x1; createTree (base_tree null);
        // createCommit (no parents); createRef.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        server.enqueue(json("""{"sha":"BLOB1"}"""))
        server.enqueue(json("""{"sha":"TREE1"}"""))
        server.enqueue(json("""{"sha":"COMMIT1"}"""))
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"COMMIT1"}}"""))

        val sut = GitTreePublisher(api, creds())
        val r = sut.publish(listOf(file("index.html", "<html>", true)), "main", "init")

        assertThat(r).isInstanceOf(GitTreePublisher.Result.Success::class.java)
        assertThat((r as GitTreePublisher.Result.Success).commitSha).isEqualTo("COMMIT1")

        val paths = mutableListOf<String>()
        repeat(5) { paths += server.takeRequest().path!! }
        // No getCommit call (no head); ends with a POST create-ref (not PATCH).
        assertThat(paths.none { it.contains("/git/commits/") }).isTrue() // getCommit is /git/commits/{sha}
        assertThat(paths.last()).endsWith("/git/refs")
    }

    @Test
    fun blob_failure_returns_before_ref_move() = runBlocking {
        // getRef ok, getCommit ok, createBlob -> 422. Must NOT createTree/commit/ref.
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"HEAD1","type":"commit"}}"""))
        server.enqueue(json("""{"sha":"HEAD1","tree":{"sha":"BASETREE"}}"""))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"bad blob"}"""))

        val sut = GitTreePublisher(api, creds())
        val r = sut.publish(listOf(file("books.json", "x", true)), "main", "msg")

        assertThat(r).isInstanceOf(GitTreePublisher.Result.HttpError::class.java)
        assertThat((r as GitTreePublisher.Result.HttpError).code).isEqualTo(422)
        // Only 3 requests were made: ref, commit, blob — never a tree/commit-create/ref-move.
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun all_unchanged_uploads_nothing_still_commits() = runBlocking {
        // No file has upload=true → zero blob creates, but we still build a tree
        // + commit (e.g. books.json content same but caller chose to republish).
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"HEAD1","type":"commit"}}"""))
        server.enqueue(json("""{"sha":"HEAD1","tree":{"sha":"BASETREE"}}"""))
        server.enqueue(json("""{"sha":"TREE2"}"""))
        server.enqueue(json("""{"sha":"COMMIT2"}"""))
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"COMMIT2"}}"""))

        val sut = GitTreePublisher(api, creds())
        val r = sut.publish(listOf(file("books.json", "same", false)), "main", "msg")

        assertThat(r).isInstanceOf(GitTreePublisher.Result.Success::class.java)
        assertThat((r as GitTreePublisher.Result.Success).uploadedPaths).isEmpty()
        assertThat(server.requestCount).isEqualTo(5) // ref, commit, tree, commit-create, ref-move; no blobs
    }
}
