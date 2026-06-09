package dev.khoj.pitaka.data.images

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for [ImageStore] cover handling (PLAN-covers.md D1).
 *
 * The key invariants: a captured cover is named with a random UUID (NOT the
 * book id), stored under `filesDir/covers/`, and returned as a relative
 * `covers/<uuid>.jpg` reference. Two captures never collide.
 */
@RunWith(RobolectricTestRunner::class)
class ImageStoreTest {

    private lateinit var context: Context
    private lateinit var store: ImageStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ImageStore(context, ImagePipeline())
    }

    /** Writes a small valid PNG to a temp file and returns a file:// Uri. */
    private fun samplePngUri(): Uri {
        val bmp = android.graphics.Bitmap.createBitmap(
            80, 120, android.graphics.Bitmap.Config.ARGB_8888,
        )
        bmp.eraseColor(android.graphics.Color.CYAN)
        val tmp = File.createTempFile("src", ".png", context.cacheDir)
        tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return Uri.fromFile(tmp)
    }

    @Test
    fun `importBookCover returns a relative covers reference`() {
        val ref = store.importBookCover(samplePngUri())
        assertThat(ref).isNotNull()
        assertThat(ref!!).startsWith("covers/")
        assertThat(ref).endsWith(".jpg")
        // Not absolute, no file://, no embedded id.
        assertThat(ref).doesNotContain("file://")
        assertThat(ref).doesNotContain("/data/")
    }

    @Test
    fun `importBookCover writes the file under filesDir covers`() {
        val ref = store.importBookCover(samplePngUri())!!
        val f = CoverPaths.absoluteCoverFile(context.filesDir, ref)!!
        assertThat(f.exists()).isTrue()
        assertThat(f.length()).isGreaterThan(0L)
    }

    @Test
    fun `two captures never collide`() {
        val a = store.importBookCover(samplePngUri())!!
        val b = store.importBookCover(samplePngUri())!!
        assertThat(a).isNotEqualTo(b)
        // Both files exist independently.
        assertThat(CoverPaths.absoluteCoverFile(context.filesDir, a)!!.exists()).isTrue()
        assertThat(CoverPaths.absoluteCoverFile(context.filesDir, b)!!.exists()).isTrue()
    }

    @Test
    fun `deleteCoverByUrl removes a relative cover file`() {
        val ref = store.importBookCover(samplePngUri())!!
        assertThat(store.deleteCoverByUrl(ref)).isTrue()
        assertThat(CoverPaths.absoluteCoverFile(context.filesDir, ref)!!.exists()).isFalse()
    }

    @Test
    fun `deleteCoverByUrl is a no-op for remote and null`() {
        assertThat(store.deleteCoverByUrl("https://covers.openlibrary.org/b/id/1-M.jpg")).isFalse()
        assertThat(store.deleteCoverByUrl(null)).isFalse()
    }

    @Test
    fun `coverFileForUrl returns the file when present and null when missing`() {
        val ref = store.importBookCover(samplePngUri())!!
        assertThat(store.coverFileForUrl(ref)).isNotNull()
        store.deleteCoverByUrl(ref)
        assertThat(store.coverFileForUrl(ref)).isNull()
    }
}
