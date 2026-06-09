package dev.khoj.pitaka.data.publish

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PublishManifestStoreTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var ctx: Context
    private lateinit var store: PublishManifestStore

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        store = PublishManifestStore(ctx, moshi)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun missing_file_loads_empty() {
        assertThat(store.load()).isEqualTo(PublishManifest.EMPTY)
        assertThat(store.load().fileShas).isEmpty()
    }

    @Test
    fun round_trips_repo_shas_and_cover_urls() {
        val m = PublishManifest(
            repo = "PLFIndia/KhojLibrary",
            fileShas = mapOf(
                "books.json" to "aaa111",
                "index.html" to "bbb222",
                "covers/3f2c.jpg" to "ccc333",
            ),
            coverUrlByBookId = mapOf(
                "1" to "https://covers.openlibrary.org/b/id/1.jpg",
                "2" to "covers/uuid-2.jpg",
            ),
        )
        store.save(m)
        val loaded = store.load()
        assertThat(loaded.repo).isEqualTo("PLFIndia/KhojLibrary")
        assertThat(loaded.shaFor("books.json")).isEqualTo("aaa111")
        assertThat(loaded.shaFor("covers/3f2c.jpg")).isEqualTo("ccc333")
        assertThat(loaded.shaFor("missing.txt")).isNull()
        assertThat(loaded.coverUrlByBookId["1"]).isEqualTo("https://covers.openlibrary.org/b/id/1.jpg")
    }

    @Test
    fun corrupt_json_degrades_to_empty_not_crash() {
        File(ctx.filesDir, PublishManifestStore.FILE_NAME)
            .writeText("{ this is not valid json ", Charsets.UTF_8)
        // Must not throw; a corrupt cache just means a full (correct) publish.
        assertThat(store.load()).isEqualTo(PublishManifest.EMPTY)
    }

    @Test
    fun clear_removes_persisted_manifest() {
        store.save(PublishManifest(repo = "x/y", fileShas = mapOf("a" to "1")))
        assertThat(store.load().fileShas).isNotEmpty()
        store.clear()
        assertThat(store.load()).isEqualTo(PublishManifest.EMPTY)
    }
}
