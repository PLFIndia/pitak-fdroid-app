package dev.khoj.pitaka.data.bundle

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.backup.BackupArchive
import dev.khoj.pitaka.data.backup.BoundedExtractionException
import dev.khoj.pitaka.data.backup.BoundedZipExtractor
import dev.khoj.pitaka.data.export.Exporters
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.images.CoverPaths
import dev.khoj.pitaka.data.import_.ImportPayload
import dev.khoj.pitaka.data.import_.PitakaJsonImporter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "Pitaka bundle" (.zip) interchange format: a portable library export that
 * carries the canonical re-importable JSON PLUS the actual local cover images,
 * and NOTHING vault-derived.
 *
 * It is the plain JSON export ([PitakaExport]: books + wishlist + library
 * namespace, D4-clean) wrapped in a ZIP so that LOCAL covers — which JSON alone
 * cannot carry (it has no image bytes; PitakaJsonImporter drops local refs) —
 * travel with the data.
 *
 * Layout (flat — borrowed from [BackupArchive] so we share [BoundedZipExtractor]):
 *   library.json          the streamed PitakaExport
 *   cover_<leaf>          one entry per referenced LOCAL cover file
 *
 * Deliberately NOT the backup archive: backup carries the encrypted DB files
 * and the passphrase-wrapped vault blob and is restored *destructively* (wipes
 * device state, re-wraps Keystore). A bundle is *merged* (add books, skip
 * duplicates) like a JSON import and contains zero vault data. The two formats
 * only share the cover-entry convention, not the security-sensitive payload.
 *
 * Remote `https://` covers need no bundling — they already round-trip as URLs
 * inside library.json.
 */
@Singleton
class LibraryBundle @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporters: Exporters,
    private val pitakaJson: PitakaJsonImporter,
) {
    /**
     * Streams a bundle ZIP to [out]. The JSON is written first (the importer
     * needs it; readers can stop early), then each referenced local cover.
     * [out]'s lifecycle is owned by the caller (we flush, do not close).
     */
    fun writeTo(export: PitakaExport, out: OutputStream) {
        // De-dup leaves: two rows could legitimately reference the same cover
        // file (e.g. after a merge), and a duplicate ZIP entry name would be
        // rejected by BoundedZipExtractor on the way back in.
        val leaves = LinkedHashSet<String>()
        (export.books.asSequence().map { it.coverUrl } +
            export.wishlist.asSequence().map { it.coverUrl })
            .forEach { ref -> CoverPaths.leafOf(ref)?.let { leaves.add(it) } }

        val coversDir = File(context.filesDir, CoverPaths.COVERS_DIR)
        val zos = ZipOutputStream(out)
        // library.json — stream via Exporters so a large library is never one
        // giant String in memory (P6). Don't close the buffered sink (it would
        // close zos's stream); Exporters.writeJson flushes.
        zos.putNextEntry(ZipEntry(ENTRY_LIBRARY_JSON))
        exporters.writeJson(export, zos)
        zos.closeEntry()

        for (leaf in leaves) {
            val file = File(coversDir, leaf)
            if (!file.isFile) continue // referenced-but-missing cover: skip, best effort
            zos.putNextEntry(ZipEntry(BackupArchive.COVER_ENTRY_PREFIX + leaf))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        zos.finish()
        zos.flush()
    }

    /** Outcome of reading a bundle. */
    sealed interface ReadResult {
        data class Success(val payload: ImportPayload) : ReadResult
        data class Failed(val reason: String) : ReadResult
    }

    /**
     * Reads a bundle from [input]: bounded-extracts the ZIP, writes the bundled
     * cover files into filesDir/covers/ (ADDITIVE — never wipes existing covers,
     * unlike a destructive restore), then parses library.json keeping the local
     * cover references so they resolve against the files just written.
     *
     * Cover filenames are random UUIDs (CoverPaths D1) so additive copy is
     * collision-free across libraries.
     */
    fun read(input: InputStream): ReadResult {
        val tempDir = File(context.cacheDir, "bundle-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val files: Map<String, File> = try {
                BoundedZipExtractor.extract(input, tempDir)
            } catch (e: BoundedExtractionException) {
                return ReadResult.Failed(e.message ?: "Bundle rejected")
            }

            val jsonFile = files[ENTRY_LIBRARY_JSON]
                ?: return ReadResult.Failed("Bundle missing $ENTRY_LIBRARY_JSON")

            // Write bundled covers into place BEFORE parsing, so the kept local
            // references point at real files. Best-effort per cover: one
            // unreadable entry must not fail the whole import.
            val coversDir = File(context.filesDir, CoverPaths.COVERS_DIR).apply { mkdirs() }
            for ((name, file) in files) {
                if (!name.startsWith(BackupArchive.COVER_ENTRY_PREFIX)) continue
                val leaf = name.removePrefix(BackupArchive.COVER_ENTRY_PREFIX)
                // Defence in depth: the extractor already sanitised the leaf;
                // re-validate via CoverPaths so a traversal name can never land.
                if (CoverPaths.leafOf(CoverPaths.PREFIX + leaf) != leaf) continue
                runCatching { file.copyTo(File(coversDir, leaf), overwrite = true) }
            }

            val payload = pitakaJson.parse(jsonFile.readText(Charsets.UTF_8), keepLocalCovers = true)
            return ReadResult.Success(payload)
        } catch (t: Throwable) {
            return ReadResult.Failed(t.message ?: "Bundle read failed")
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    companion object {
        const val ENTRY_LIBRARY_JSON = "library.json"
    }
}
