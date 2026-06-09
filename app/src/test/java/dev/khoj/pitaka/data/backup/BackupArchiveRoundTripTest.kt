package dev.khoj.pitaka.data.backup

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Test

/**
 * Pure-JVM unit test: write a zip via the same shape BackupArchive uses,
 * then verify entry layout + manifest round-trip. We don't instantiate
 * BackupArchive itself because it touches Context, Moshi-via-Hilt, and
 * the real DB files. The contract under test is "manifest + named entries
 * stored in a zip survive a round-trip".
 */
class BackupArchiveRoundTripTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun zip_with_manifest_and_db_files_roundtrips_intact() {
        val booksBytes = "books-db-bytes".toByteArray()
        val wishlistBytes = "wishlist-db-bytes".toByteArray()
        val blobBytes = "wrapped-passphrase-blob".toByteArray()
        val manifest = BackupManifest(
            exportedAt = 99L,
            hasBooks = true,
            hasWishlist = true,
            hasBorrowers = false,
            hasBackupBlob = true,
            backupHint = "remember the alamo",
        )

        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zos.write(moshi.adapter<BackupManifest>().toJson(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("books.db"))
            zos.write(booksBytes); zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("wishlist.db"))
            zos.write(wishlistBytes); zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("backup_blob"))
            zos.write(blobBytes); zos.closeEntry()
        }

        val readBack = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                readBack[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        assertThat(readBack.keys).containsExactly(
            "manifest.json", "books.db", "wishlist.db", "backup_blob",
        )
        assertThat(readBack["books.db"]).isEqualTo(booksBytes)
        assertThat(readBack["wishlist.db"]).isEqualTo(wishlistBytes)
        assertThat(readBack["backup_blob"]).isEqualTo(blobBytes)

        val manifestParsed = moshi.adapter<BackupManifest>()
            .fromJson(String(readBack["manifest.json"]!!, Charsets.UTF_8))!!
        assertThat(manifestParsed.exportedAt).isEqualTo(99L)
        assertThat(manifestParsed.hasBorrowers).isFalse()
        assertThat(manifestParsed.backupHint).isEqualTo("remember the alamo")
    }

    /**
     * PLAN-covers.md D2: covers are bundled as FLAT `cover_<leaf>` entries
     * (the archive + BoundedZipExtractor reject nested paths). This verifies
     * the flat cover entries survive the REAL bounded extractor and that the
     * prefix-strip recovers the on-disk leaf, which restore routes to
     * filesDir/covers/<leaf>.
     */
    @Test
    fun flat_cover_entries_survive_the_real_bounded_extractor() {
        val uuidLeaf = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6.jpg"
        val coverBytes = ByteArray(256) { (it and 0xFF).toByte() }

        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zos.write("""{"schemaVersion":1,"exportedAt":1,"hasCovers":true}""".toByteArray())
            zos.closeEntry()
            // Bundled cover, exactly as BackupArchive.write emits it.
            zos.putNextEntry(java.util.zip.ZipEntry(BackupArchive.COVER_ENTRY_PREFIX + uuidLeaf))
            zos.write(coverBytes); zos.closeEntry()
        }

        val tempDir = java.io.File.createTempFile("cover-rt-", "").apply {
            delete(); mkdirs()
        }
        try {
            val extracted = BoundedZipExtractor.extract(
                ByteArrayInputStream(out.toByteArray()), tempDir,
            )
            // The flat cover entry survived (a nested covers/<leaf> would have
            // been rejected as an unsafe filename).
            val coverEntryName = BackupArchive.COVER_ENTRY_PREFIX + uuidLeaf
            assertThat(extracted.keys).contains(coverEntryName)
            assertThat(extracted[coverEntryName]!!.readBytes()).isEqualTo(coverBytes)
            // Prefix-strip recovers the on-disk leaf restore writes under covers/.
            val recoveredLeaf = coverEntryName.removePrefix(BackupArchive.COVER_ENTRY_PREFIX)
            assertThat(recoveredLeaf).isEqualTo(uuidLeaf)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
