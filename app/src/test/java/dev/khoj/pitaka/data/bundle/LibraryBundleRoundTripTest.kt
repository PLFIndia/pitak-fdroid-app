package dev.khoj.pitaka.data.bundle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.export.Exporters
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.images.CoverPaths
import dev.khoj.pitaka.data.import_.PitakaJsonImporter
import dev.khoj.pitaka.domain.model.Book
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Round-trips a library bundle: a local cover referenced by a book must travel
 * inside the ZIP and land back in filesDir/covers/ on read, with the book's
 * local reference preserved (NOT dropped the way plain-JSON import drops it).
 * Also verifies a remote cover passes through untouched and a referenced-but-
 * missing local cover is skipped without failing the export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryBundleRoundTripTest {

    private lateinit var context: Context
    private lateinit var sut: LibraryBundle
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sut = LibraryBundle(context, Exporters(moshi), PitakaJsonImporter(moshi))
        // Clean covers dir between runs.
        File(context.filesDir, CoverPaths.COVERS_DIR).deleteRecursively()
    }

    private fun writeCover(leaf: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, CoverPaths.COVERS_DIR).apply { mkdirs() }
        File(dir, leaf).writeBytes(bytes)
        return CoverPaths.PREFIX + leaf
    }

    @Test
    fun bundle_carries_local_cover_bytes_and_restores_them() {
        val coverBytes = byteArrayOf(1, 2, 3, 4, 5)
        val localRef = writeCover("abcd-1111.jpg", coverBytes)
        val export = PitakaExport(
            exportedAt = 0L,
            books = listOf(
                Book(id = 7L, title = "With Local Cover", isbn = "111", coverUrl = localRef),
                Book(id = 8L, title = "With Remote Cover", isbn = "222", coverUrl = "https://example.com/c.jpg"),
                Book(id = 9L, title = "No Cover", isbn = "333", coverUrl = null),
            ),
            wishlist = emptyList(),
            libraryId = "lib-xyz",
            libraryName = "My Library",
        )

        // Export → bytes
        val out = ByteArrayOutputStream()
        sut.writeTo(export, out)

        // Wipe the on-disk cover so we prove the ZIP restored it, not the leftover.
        File(context.filesDir, CoverPaths.COVERS_DIR).deleteRecursively()

        // Import ← bytes
        val result = sut.read(ByteArrayInputStream(out.toByteArray()))
        assertThat(result).isInstanceOf(LibraryBundle.ReadResult.Success::class.java)
        val payload = (result as LibraryBundle.ReadResult.Success).payload

        // Local cover ref preserved (kept, not dropped) ...
        val withLocal = payload.books.first { it.title == "With Local Cover" }
        assertThat(withLocal.coverUrl).isEqualTo(localRef)
        // ... and the bytes were restored to filesDir/covers/.
        val restored = File(File(context.filesDir, CoverPaths.COVERS_DIR), "abcd-1111.jpg")
        assertThat(restored.exists()).isTrue()
        assertThat(restored.readBytes()).isEqualTo(coverBytes)

        // Remote cover passes through untouched.
        val withRemote = payload.books.first { it.title == "With Remote Cover" }
        assertThat(withRemote.coverUrl).isEqualTo("https://example.com/c.jpg")

        // ids reset to 0 on import (fresh-row contract).
        assertThat(payload.books.map { it.id }.toSet()).containsExactly(0L)
    }

    @Test
    fun missing_local_cover_is_skipped_not_fatal() {
        // Book references a local cover whose file does not exist on disk.
        val export = PitakaExport(
            exportedAt = 0L,
            books = listOf(Book(id = 1L, title = "Dangling", isbn = "999", coverUrl = "covers/does-not-exist.jpg")),
            wishlist = emptyList(),
            libraryId = "lib-xyz",
            libraryName = "My Library",
        )
        val out = ByteArrayOutputStream()
        sut.writeTo(export, out) // must not throw

        val result = sut.read(ByteArrayInputStream(out.toByteArray()))
        assertThat(result).isInstanceOf(LibraryBundle.ReadResult.Success::class.java)
        val payload = (result as LibraryBundle.ReadResult.Success).payload
        // Reference is kept (bundle mode), but no file was bundled or restored.
        assertThat(payload.books.first().coverUrl).isEqualTo("covers/does-not-exist.jpg")
        val restored = File(File(context.filesDir, CoverPaths.COVERS_DIR), "does-not-exist.jpg")
        assertThat(restored.exists()).isFalse()
    }

    @Test
    fun read_rejects_a_non_bundle_zip_missing_library_json() {
        // A ZIP with only a cover entry, no library.json.
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("cover_x.jpg"))
            zos.write(byteArrayOf(9))
            zos.closeEntry()
        }
        val result = sut.read(ByteArrayInputStream(out.toByteArray()))
        assertThat(result).isInstanceOf(LibraryBundle.ReadResult.Failed::class.java)
    }
}
