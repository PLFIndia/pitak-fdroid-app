package dev.khoj.pitaka.data.remote.openlibrary

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.domain.lookup.LookupResult
import dev.khoj.pitaka.domain.lookup.SearchResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * MockWebServer-driven contract tests for the Open Library client.
 *
 * Verifies (a) the live JSON shape parses into our DTOs, (b) the mapping into
 * the Pitaka domain model preserves the fields we promise, (c) graceful
 * degradation on missing-field and empty responses (§1.1).
 */
class OpenLibraryApiTest {

    private lateinit var server: MockWebServer
    private lateinit var service: OpenLibraryLookupService

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenLibraryApi::class.java)
        service = OpenLibraryLookupService(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun lookupByIsbn_parses_real_world_shape() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE_OL_BOOK_JSON))
        val r = service.lookupByIsbn("9780140428445")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        val metadata = (r as LookupResult.Found).metadata
        assertThat(metadata.title).isEqualTo("Tractatus Logico-Philosophicus")
        assertThat(metadata.author).isEqualTo("Ludwig Wittgenstein")
        assertThat(metadata.publisher).isEqualTo("Penguin Classics")
        assertThat(metadata.publishedYear).isEqualTo(1922)
        assertThat(metadata.pageCount).isEqualTo(96)
        assertThat(metadata.coverUrl).contains("openlibrary.org")
        assertThat(metadata.genre).contains("Philosophy")
    }

    @Test
    fun lookupByIsbn_returns_NotFound_on_empty_map() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val r = service.lookupByIsbn("9780000000000")
        assertThat(r).isEqualTo(LookupResult.NotFound)
    }

    @Test
    fun lookupByIsbn_returns_NetworkError_on_5xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val r = service.lookupByIsbn("9780140428445")
        assertThat(r).isInstanceOf(LookupResult.NetworkError::class.java)
    }

    @Test
    fun searchByTitle_parses_results() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE_OL_SEARCH_JSON))
        val r = service.searchByTitle("godaan")
        assertThat(r).isInstanceOf(SearchResult.Found::class.java)
        val results = (r as SearchResult.Found).results
        assertThat(results).isNotEmpty()
        assertThat(results.first().title).isEqualTo("Godaan")
        assertThat(results.first().author).isEqualTo("Premchand")
        assertThat(results.first().publishedYear).isEqualTo(1936)
    }

    @Test
    fun searchByTitle_returns_Empty_on_zero_docs() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"numFound":0,"docs":[]}"""))
        val r = service.searchByTitle("zzzzzz")
        assertThat(r).isEqualTo(SearchResult.Empty)
    }
}

// --- Sample payloads (verified shape against live API) ---

private val SAMPLE_OL_BOOK_JSON = """
{
  "ISBN:9780140428445": {
    "title": "Tractatus Logico-Philosophicus",
    "authors": [{"name": "Ludwig Wittgenstein"}],
    "publishers": [{"name": "Penguin Classics"}],
    "publish_date": "January 1922",
    "number_of_pages": 96,
    "cover": {
      "medium": "https://covers.openlibrary.org/b/id/123-M.jpg"
    },
    "subjects": [{"name": "Philosophy"}, {"name": "Logic"}]
  }
}
""".trimIndent()

private val SAMPLE_OL_SEARCH_JSON = """
{
  "numFound": 1,
  "docs": [
    {
      "key": "/works/OL12345W",
      "title": "Godaan",
      "author_name": ["Premchand"],
      "first_publish_year": 1936,
      "isbn": ["9788121615568"],
      "cover_i": 9876
    }
  ]
}
""".trimIndent()
