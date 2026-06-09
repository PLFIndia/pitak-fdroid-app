package dev.khoj.pitaka.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * F-02 (audit): the backup restorer used to extract any ZIP a user
 * handed it with no caps on size or count, so a 1KB malicious archive
 * could decompress into gigabytes and wedge the device.
 *
 * This helper extracts a flat ZIP into [tempDir] with strict limits:
 *   - Each entry's decompressed size <= [Limits.maxEntryBytes].
 *   - Sum of all decompressed sizes <= [Limits.maxTotalBytes].
 *   - Total entry count <= [Limits.maxEntries].
 *
 * It also enforces zip-slip protection two ways: filenames are reduced
 * to their leaf (any directory component stripped), and the destination
 * file's canonical path must lie under [tempDir]'s canonical path. The
 * caller does not need to sanitise inputs.
 *
 * The Pitaka backup archive is intentionally flat (manifest + a handful
 * of DB files at top level); rejecting any entry that contains a `/` or
 * `\\` after trimming, or whose leaf name is empty, is the right
 * behaviour for this codebase and surfaces tampering loudly.
 *
 * Design borrowed from Signal Android's BackupImporter size accounting:
 * count bytes as the inflater produces them, fail fast on a limit. Don't
 * trust the ZIP's central-directory sizes \u2014 those are attacker-supplied.
 *
 * Returns a map from sanitised entry name to the file on disk. Caller
 * owns [tempDir] and is responsible for cleaning it up; on exception we
 * leave whatever partial extraction occurred for the caller to delete.
 *
 * @throws BoundedExtractionException on any limit violation or
 *         structural anomaly (zip-slip, empty name, nested name).
 * @throws IOException on underlying ZIP / IO errors.
 */
object BoundedZipExtractor {

    data class Limits(
        val maxEntryBytes: Long,
        val maxTotalBytes: Long,
        val maxEntries: Int,
    ) {
        init {
            require(maxEntryBytes > 0) { "maxEntryBytes must be positive" }
            require(maxTotalBytes > 0) { "maxTotalBytes must be positive" }
            require(maxEntries > 0)    { "maxEntries must be positive" }
            require(maxTotalBytes >= maxEntryBytes) {
                "maxTotalBytes ($maxTotalBytes) must be >= maxEntryBytes ($maxEntryBytes)"
            }
        }
    }

    /** Defaults for the Pitaka backup format; see audit F-02 for rationale. */
    val PITAKA_BACKUP_LIMITS: Limits = Limits(
        maxEntryBytes = 200L * 1024 * 1024,  // 200 MiB
        maxTotalBytes = 500L * 1024 * 1024,  // 500 MiB
        // 5 fixed top-level entries (manifest + 3 DBs + blob) plus one entry
        // per bundled cover (PLAN-covers.md D2). Covers are downscaled to
        // ~50KB, so the 500 MiB TOTAL-bytes cap is the real zip-bomb guard;
        // this count cap is a generous structural ceiling (~4000 covers) that
        // still fails a pathological many-tiny-entries archive loudly.
        maxEntries = 4096,
    )

    fun extract(
        input: InputStream,
        tempDir: File,
        limits: Limits = PITAKA_BACKUP_LIMITS,
    ): Map<String, File> {
        require(tempDir.isDirectory) { "tempDir must exist and be a directory: $tempDir" }
        val tempCanonical = tempDir.canonicalPath
        val tempCanonicalPrefix = tempCanonical + File.separator
        val out = LinkedHashMap<String, File>()
        var totalBytes = 0L
        var entryCount = 0
        val readBuf = ByteArray(BUFFER_SIZE)

        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > limits.maxEntries) {
                    throw BoundedExtractionException(
                        "Archive has too many entries (>${limits.maxEntries})"
                    )
                }

                val rawName = entry.name
                // Reject directory entries outright \u2014 the Pitaka archive is flat.
                if (entry.isDirectory) {
                    throw BoundedExtractionException(
                        "Archive contains a directory entry: '$rawName'"
                    )
                }
                val leaf = rawName.substringAfterLast('/').substringAfterLast('\\')
                if (leaf.isBlank()) {
                    throw BoundedExtractionException(
                        "Archive entry has an empty filename: '$rawName'"
                    )
                }
                if (leaf.contains('/') || leaf.contains('\\') || leaf.contains('\u0000')) {
                    throw BoundedExtractionException(
                        "Archive entry has an unsafe filename: '$rawName'"
                    )
                }
                if (leaf in out) {
                    throw BoundedExtractionException(
                        "Archive has duplicate entry name: '$leaf'"
                    )
                }

                val dest = File(tempDir, leaf)
                val destCanonical = dest.canonicalPath
                if (destCanonical != tempCanonical + File.separator + leaf &&
                    !destCanonical.startsWith(tempCanonicalPrefix)
                ) {
                    // Defence-in-depth zip-slip catch \u2014 the leaf strip above
                    // already prevents this, but a future refactor that
                    // weakens the strip will still trip this check.
                    throw BoundedExtractionException(
                        "Archive entry escapes tempDir: '$rawName' \u2192 '$destCanonical'"
                    )
                }

                var entryBytes = 0L
                dest.outputStream().use { fos ->
                    while (true) {
                        val n = zis.read(readBuf)
                        if (n < 0) break
                        entryBytes += n
                        totalBytes += n
                        if (entryBytes > limits.maxEntryBytes) {
                            throw BoundedExtractionException(
                                "Archive entry '$leaf' exceeds per-entry cap " +
                                    "(${limits.maxEntryBytes} bytes)"
                            )
                        }
                        if (totalBytes > limits.maxTotalBytes) {
                            throw BoundedExtractionException(
                                "Archive total exceeds cap " +
                                    "(${limits.maxTotalBytes} bytes)"
                            )
                        }
                        fos.write(readBuf, 0, n)
                    }
                }
                out[leaf] = dest
                entry = zis.nextEntry
            }
        }
        return out
    }

    private const val BUFFER_SIZE: Int = 16 * 1024
}

/**
 * Thrown by [BoundedZipExtractor.extract] on any limit violation or
 * structural anomaly. Distinct from [IOException] so the caller can
 * map it to a user-visible reason ("archive looks corrupt or hostile")
 * without conflating it with disk-full / permission errors.
 */
class BoundedExtractionException(message: String) : IOException(message)
