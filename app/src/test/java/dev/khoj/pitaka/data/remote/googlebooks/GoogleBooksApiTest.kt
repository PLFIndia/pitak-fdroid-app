package dev.khoj.pitaka.data.remote.googlebooks

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

class GoogleBooksApiTest {

    private lateinit var server: MockWebServer
    private lateinit var service: GoogleBooksLookupService

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleBooksApi::class.java)
        service = GoogleBooksLookupService(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun lookupByIsbn_parses_a_typical_response() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE_GB_ISBN_JSON))
        val r = service.lookupByIsbn("9780140428445")
        assertThat(r).isInstanceOf(LookupResult.Found::class.java)
        val m = (r as LookupResult.Found).metadata
        assertThat(m.title).isEqualTo("Tractatus Logico-Philosophicus")
        assertThat(m.author).isEqualTo("Ludwig Wittgenstein")
        assertThat(m.publisher).isEqualTo("Penguin Classics")
        assertThat(m.publishedYear).isEqualTo(1922)
        assertThat(m.pageCount).isEqualTo(96)
        assertThat(m.language).isEqualTo("en")
        assertThat(m.genre).contains("Philosophy")
    }

    @Test
    fun lookupByIsbn_returns_NotFound_when_no_items() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"totalItems":0}"""))
        val r = service.lookupByIsbn("9780000000000")
        assertThat(r).isEqualTo(LookupResult.NotFound)
    }

    @Test
    fun searchByTitle_returns_Found() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE_GB_SEARCH_JSON))
        val r = service.searchByTitle("godaan")
        assertThat(r).isInstanceOf(SearchResult.Found::class.java)
        val first = (r as SearchResult.Found).results.first()
        assertThat(first.title).isEqualTo("Godaan")
        assertThat(first.isbn).isEqualTo("9788121615568")
    }

    @Test
    fun lookupByIsbn_returns_NetworkError_on_500() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = service.lookupByIsbn("9780140428445")
        assertThat(r).isInstanceOf(LookupResult.NetworkError::class.java)
    }
}

private val SAMPLE_GB_ISBN_JSON = """
{
  "totalItems": 1,
  "items": [
    {
      "id": "1234",
      "volumeInfo": {
        "title": "Tractatus Logico-Philosophicus",
        "authors": ["Ludwig Wittgenstein"],
        "publisher": "Penguin Classics",
        "publishedDate": "1922-01-01",
        "pageCount": 96,
        "categories": ["Philosophy"],
        "language": "en",
        "imageLinks": {
          "thumbnail": "https://books.google.com/cover.jpg"
        },
        "industryIdentifiers": [
          {"type": "ISBN_13", "identifier": "9780140428445"}
        ]
      }
    }
  ]
}
""".trimIndent()

private val SAMPLE_GB_SEARCH_JSON = """
{
  "totalItems": 1,
  "items": [
    {
      "id": "abcd",
      "volumeInfo": {
        "title": "Godaan",
        "authors": ["Premchand"],
        "publishedDate": "1936",
        "industryIdentifiers": [
          {"type": "ISBN_13", "identifier": "9788121615568"}
        ]
      }
    }
  ]
}
""".trimIndent()
