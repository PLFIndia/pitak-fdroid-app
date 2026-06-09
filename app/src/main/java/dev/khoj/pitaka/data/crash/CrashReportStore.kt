package dev.khoj.pitaka.data.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device storage for crash reports.
 *
 * Reports live as plain-text `.txt` files in `filesDir/crashes/`, written by
 * [CrashHandler] from inside the dying process. They are read back on next
 * launch by [CrashReportViewModel] / Settings so the user can submit one to
 * the public crash-log GitHub repo or delete it.
 *
 * Design choices (per project §1.1 — no developer infrastructure):
 *   - Plain text, human-readable. The user can open one with any text app.
 *   - Cap at the [MAX_REPORTS] most recent reports — a crash loop must not
 *     fill the filesystem. Oldest reports are pruned on next write.
 *   - App-private storage. Reports do not appear in user-visible Files apps,
 *     do not get included in encrypted Pitak backups, and disappear on
 *     uninstall. They only leave the device when the user explicitly opens
 *     the prefilled GitHub Issue URL.
 *   - No PII beyond what [CrashHandler] writes (stack trace + Pitak version +
 *     Android version + device model + locale + timestamp). Exception
 *     messages are stripped at write time by [CrashHandler.format].
 */
@Singleton
class CrashReportStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Resolved lazily so unit tests using a [Context] with no `filesDir` set fail loudly. */
    private val crashDir: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /**
     * Persists a freshly-formatted crash report to disk and prunes older
     * reports so at most [MAX_REPORTS] remain. Returns the file written, or
     * `null` if the write itself failed (we are typically inside a dying
     * process — never throw).
     *
     * Filenames include both a UTC timestamp and a short random suffix so
     * rapid back-to-back writes (e.g. a crash loop within the same ms) get
     * distinct files.
     */
    fun write(formattedBody: String, atEpochMillis: Long = System.currentTimeMillis()): File? =
        runCatching {
            val file = uniqueFileFor(atEpochMillis)
            file.writeText(formattedBody, Charsets.UTF_8)
            prune()
            file
        }.getOrNull()

    /** All crash files, newest first. Survives empty/missing directory. */
    fun list(): List<CrashReportFile> {
        val dir = crashDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f -> CrashReportFile(file = f, timestampMillis = f.lastModified()) }
            ?: emptyList()
    }

    /** True when at least one crash report is on disk and the user has not deleted/sent it yet. */
    fun hasAny(): Boolean = list().isNotEmpty()

    fun count(): Int = list().size

    /** Read the full report body for display / clipboard / submission. */
    fun read(file: File): String? = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()

    /** Delete one report; the user's explicit "I don't want to send this" path. */
    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** Delete all reports — the "clear" entry in the Settings list. */
    fun deleteAll() {
        crashDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** Keep newest [MAX_REPORTS] only. Called after every write. */
    private fun prune() {
        val files = list()
        if (files.size <= MAX_REPORTS) return
        files.drop(MAX_REPORTS).forEach { runCatching { it.file.delete() } }
    }

    /**
     * Build a non-colliding file inside [crashDir]. The base name is
     * timestamped to millisecond precision; if a file with that name
     * already exists (crash loop within the same ms), we append a short
     * incrementing suffix until we find a free name.
     */
    private fun uniqueFileFor(epochMillis: Long): File {
        val stamp = FILE_TIMESTAMP_FORMAT.get()!!.format(Date(epochMillis))
        val base = "crash-$stamp"
        var candidate = File(crashDir, base + SUFFIX)
        var n = 1
        while (candidate.exists()) {
            candidate = File(crashDir, "$base-$n$SUFFIX")
            n++
        }
        return candidate
    }

    companion object {
        /** Subdirectory of `filesDir` for crash reports. */
        const val DIRECTORY_NAME = "crashes"
        /** Hard cap so a crash loop cannot blow up the user's storage. */
        const val MAX_REPORTS = 5
        private const val SUFFIX = ".txt"

        /** UTC; safe for filenames across timezone changes. */
        private val FILE_TIMESTAMP_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }
    }
}

/** UI-friendly view of one stored crash report. */
data class CrashReportFile(
    val file: File,
    val timestampMillis: Long,
)
