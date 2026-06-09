package dev.khoj.pitaka.data.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * F-02 (audit): unit tests for [BoundedZipExtractor]. Pure JVM \u2014 no
 * Robolectric, no Context. Fixtures are built with the standard
 * java.util.zip writer so the tests exercise the same code path
 * production ZipInputStream takes.
 */
class BoundedZipExtractorTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File.createTempFile("bze-test-", "")
        assertThat(tempDir.delete()).isTrue()
        assertThat(tempDir.mkdirs()).isTrue()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // --- happy path -------------------------------------------------------

    @Test
    fun happy_path_extracts_named_entries_with_payload_intact() {
        val zip = zipOf(
            "manifest.json" to """{"hello":"world"}""".toByteArray(),
            "books.db" to ByteArray(1024) { (it and 0xFF).toByte() },
        )
        val out = BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        assertThat(out.keys).containsExactly("manifest.json", "books.db").inOrder()
        assertThat(out["manifest.json"]!!.readText()).isEqualTo("""{"hello":"world"}""")
        assertThat(out["books.db"]!!.readBytes()).hasLength(1024)
    }

    @Test
    fun extracted_files_live_under_tempDir() {
        val zip = zipOf("manifest.json" to "x".toByteArray())
        val out = BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        val canonical = out["manifest.json"]!!.canonicalPath
        assertThat(canonical).startsWith(tempDir.canonicalPath + File.separator)
    }

    // --- caps -------------------------------------------------------------

    @Test
    fun per_entry_cap_trips_when_an_entry_is_too_big() {
        val limits = BoundedZipExtractor.Limits(
            maxEntryBytes = 1024L,
            maxTotalBytes = 1024L * 1024,
            maxEntries = 32,
        )
        val zip = zipOf("books.db" to ByteArray(2048) { 0 })
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir, limits)
        }
        assertThat(ex).isInstanceOf(BoundedExtractionException::class.java)
        assertThat(ex.message).contains("per-entry cap")
    }

    @Test
    fun total_cap_trips_across_multiple_entries() {
        val limits = BoundedZipExtractor.Limits(
            maxEntryBytes = 2048L,
            maxTotalBytes = 3000L,
            maxEntries = 32,
        )
        val zip = zipOf(
            "a.db" to ByteArray(2000) { 0 },
            "b.db" to ByteArray(2000) { 0 }, // 4000 total > 3000 cap
        )
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir, limits)
        }
        assertThat(ex.message).contains("total exceeds cap")
    }

    @Test
    fun entry_count_cap_trips() {
        val limits = BoundedZipExtractor.Limits(
            maxEntryBytes = 1024L,
            maxTotalBytes = 1024L * 1024,
            maxEntries = 3,
        )
        val zip = zipOf(
            "a" to "x".toByteArray(),
            "b" to "x".toByteArray(),
            "c" to "x".toByteArray(),
            "d" to "x".toByteArray(),
        )
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir, limits)
        }
        assertThat(ex.message).contains("too many entries")
    }

    // --- zip-bomb (compression ratio) -------------------------------------

    @Test
    fun highly_compressible_entry_caught_by_per_entry_cap() {
        // 4 MiB of zeros compresses to a few hundred bytes; per-entry cap
        // is what stops this from blowing up RAM/disk during inflate.
        val payload = ByteArray(4 * 1024 * 1024) { 0 }
        val zip = zipOf("bomb" to payload)
        val limits = BoundedZipExtractor.Limits(
            maxEntryBytes = 1024L * 1024, // 1 MiB
            maxTotalBytes = 8L * 1024 * 1024,
            maxEntries = 32,
        )
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir, limits)
        }
        assertThat(ex.message).contains("per-entry cap")
        // And we should not have produced a 4 MiB file on disk \u2014 we should
        // have bailed early in the streaming write.
        val anyHugeFile = tempDir.listFiles()?.any { it.length() > 1_500_000 } ?: false
        assertThat(anyHugeFile).isFalse()
    }

    // --- zip-slip and name sanitisation -----------------------------------

    @Test
    fun zip_slip_via_parent_traversal_is_blocked() {
        // After leaf-strip "../../etc/passwd" becomes "passwd" \u2014 the
        // sanitisation alone defeats classic zip-slip. We verify the
        // entry lands as a leaf file under tempDir, NOT outside it.
        val zip = zipOf("../../etc/passwd" to "pwned".toByteArray())
        val out = BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        assertThat(out.keys).containsExactly("passwd")
        val landed = out["passwd"]!!.canonicalPath
        assertThat(landed).startsWith(tempDir.canonicalPath + File.separator)
        // The two assertions above together prove the entry could not
        // have escaped tempDir; we deliberately do NOT read /etc/passwd
        // as a sanity step because sandboxed CI runners may deny it.
    }

    @Test
    fun backslash_path_traversal_is_blocked() {
        // Windows-style separator should also be leaf-stripped.
        val zip = zipOf("..\\..\\windows\\system32.dll" to "x".toByteArray())
        val out = BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        assertThat(out.keys).containsExactly("system32.dll")
    }

    @Test
    fun empty_filename_rejected() {
        val zip = zipOf("" to "x".toByteArray())
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        }
        assertThat(ex.message).contains("empty filename")
    }

    @Test
    fun directory_entry_rejected() {
        val raw = ByteArrayOutputStream()
        ZipOutputStream(raw).use { zos ->
            val e = ZipEntry("subdir/")
            zos.putNextEntry(e)
            zos.closeEntry()
        }
        val ex = assertThrows {
            BoundedZipExtractor.extract(raw.toByteArray().inputStream(), tempDir)
        }
        assertThat(ex.message).contains("directory entry")
    }

    @Test
    fun duplicate_leaf_names_rejected() {
        // After leaf-stripping, two distinct full names collapse to the
        // same leaf. Java's ZipOutputStream accepts these because their
        // raw names differ; the production guard fires on the leaf.
        val zip = zipOf(
            "a/manifest.json" to "first".toByteArray(),
            "b/manifest.json" to "second".toByteArray(),
        )
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        }
        assertThat(ex.message).contains("duplicate")
    }

    @Test
    fun null_byte_in_name_rejected() {
        // Construct an entry name containing a NUL; after leaf-strip the
        // unsafe-char check should fire.
        val zip = zipOf("evil\u0000name" to "x".toByteArray())
        val ex = assertThrows {
            BoundedZipExtractor.extract(zip.inputStream(), tempDir)
        }
        assertThat(ex.message).contains("unsafe filename")
    }

    // --- limit invariants -------------------------------------------------

    @Test
    fun limits_constructor_rejects_nonsense() {
        assertThrows {
            BoundedZipExtractor.Limits(maxEntryBytes = 0L, maxTotalBytes = 1L, maxEntries = 1)
        }
        assertThrows {
            BoundedZipExtractor.Limits(maxEntryBytes = 100L, maxTotalBytes = 50L, maxEntries = 1)
        }
        assertThrows {
            BoundedZipExtractor.Limits(maxEntryBytes = 1L, maxTotalBytes = 1L, maxEntries = 0)
        }
    }

    @Test
    fun pitaka_backup_defaults_are_sane() {
        val d = BoundedZipExtractor.PITAKA_BACKUP_LIMITS
        // Per-entry <= total, total well under "wedge the device".
        assertThat(d.maxEntryBytes).isAtMost(d.maxTotalBytes)
        assertThat(d.maxTotalBytes).isAtMost(2L * 1024 * 1024 * 1024) // 2 GiB ceiling
        assertThat(d.maxEntries).isAtLeast(5) // current archive has 5 fixed entries
        // Raised from 1000 → ~4000 when covers became per-entry bundled
        // (PLAN-covers.md D2): 5 fixed entries + one per cover. The TOTAL-bytes
        // cap (500 MiB) remains the real zip-bomb guard; this count is a
        // structural ceiling allowing a large-but-sane personal library.
        assertThat(d.maxEntries).isAtMost(8192)
    }

    // --- helpers ----------------------------------------------------------

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun assertThrows(block: () -> Unit): Throwable {
        try {
            block()
        } catch (t: Throwable) {
            return t
        }
        throw AssertionError("expected the block to throw, but it returned normally")
    }
}
