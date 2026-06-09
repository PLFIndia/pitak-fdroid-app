package dev.khoj.pitaka.data.crash

import android.content.Context
import android.os.Build
import dev.khoj.pitaka.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Installs the process-wide uncaught exception handler that captures
 * Java/Kotlin crashes to a `.txt` file via [CrashReportStore], then chains
 * to whatever handler was previously installed so Android still shows the
 * "App stopped" dialog (D - no swallowing).
 *
 * What this catches: any exception that escapes a JVM thread (the vast
 * majority of crashes in this app).
 *
 * What this does NOT catch:
 *   - Native (NDK / SIGSEGV) crashes — would need a tombstone reader or a
 *     library like xCrash / Crashlytics' NDK component. Out of scope.
 *   - ANRs (main-thread hangs killed by the system) — out of scope.
 *
 * Privacy posture: see [format]. Exception **messages** are stripped because
 * code paths can interpolate user data (book titles, borrower names) into
 * `throw IllegalStateException("...")`. Stack frames + types are kept; that
 * is all the developer needs to diagnose, and it contains no user data.
 */
object CrashHandler {

    @Volatile
    private var installed = false

    /** Idempotent. Safe to call from [dev.khoj.pitaka.PitakaApplication.onCreate]. */
    fun install(context: Context, store: CrashReportStore) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write to disk first; on any failure we still want the OS to handle
            // the crash normally so we never swallow it.
            runCatching {
                val body = format(appContext, thread, throwable)
                store.write(body)
            }
            // Hand off to the previous handler so Android's normal crash UI fires.
            // If there was no previous handler (extremely unusual), the process
            // will exit on return from this callback.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Builds the body that gets written to disk AND copied to the clipboard /
     * sent to GitHub. Plain text; readable by the user before they submit.
     *
     * Layout:
     *   ```
     *   Pitak crash report
     *   =================
     *   Pitak version : 1.0.0 (2)
     *   Android       : 14 (API 34)
     *   Device        : Google Pixel 7
     *   Locale        : en_IN
     *   Timestamp     : 2026-06-01T15:42:31Z
     *   Thread        : main
     *
     *   <ExceptionClass>
     *       at frame...
     *       at frame...
     *   Caused by: <ExceptionClass>
     *       at frame...
     *   ```
     *
     * Exception **messages** are stripped. Each line of [throwable]'s rendered
     * stack trace that starts with the exception class name has its `: ...`
     * portion dropped — type and frames only.
     */
    fun format(context: Context, thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder(2048)
        sb.append("Pitak crash report\n")
        sb.append("=================\n")
        sb.append("Pitak version : ")
            .append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("Android       : ")
            .append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Device        : ")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Locale        : ").append(currentLocale(context)).append('\n')
        sb.append("Timestamp     : ").append(ISO_8601.format(Date())).append('\n')
        sb.append("Thread        : ").append(thread.name).append('\n')
        sb.append('\n')
        sb.append(stripMessages(stackTraceToString(throwable)))
        return sb.toString()
    }

    private fun currentLocale(context: Context): String {
        val cfg = context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cfg.locales[0]
        } else {
            @Suppress("DEPRECATION") cfg.locale
        }
        return locale.toString()
    }

    private fun stackTraceToString(t: Throwable): String =
        StringWriter().also { sw -> PrintWriter(sw).use { pw -> t.printStackTrace(pw) } }.toString()

    /**
     * Strip the human-readable message from a printed stack trace, keeping
     * only the exception types and the frames — never any free-form message
     * text, which can contain user PII (book titles, borrower names).
     *
     * **Allowlist, not blocklist (F-20).** A printed exception message can
     * span MULTIPLE lines: only the first sits on the `<FQN>: <message>`
     * header; the rest render as standalone continuation lines. An earlier
     * version stripped the header but *passed through* any line it did not
     * recognise, so those continuation lines leaked verbatim into the public
     * crash-report (clipboard + GitHub issue). This version inverts the
     * default: a line is emitted ONLY if it is recognised as safe, otherwise
     * it is dropped. The categories that survive:
     *
     *  - **blank lines** — structural, no content.
     *  - **frame lines** (`	at …`) and **summary lines** (`	... N more`) —
     *    always indented in a printed trace; carry no user data.
     *  - **header lines** — the top-level first line, and any `Caused by:` /
     *    `Suppressed:` line — emitted with the message tail removed so only
     *    the exception type remains.
     *
     * Anything else — a message-continuation line, or any shape we don't
     * recognise — is dropped. Dropping (vs. passing through) is the safe
     * default for a privacy filter: the worst case is a slightly less
     * detailed crash report, never a leak.
     *
     * Examples:
     *   `java.lang.IllegalStateException: Cannot lend book 'Mom'` →
     *   `java.lang.IllegalStateException`
     *
     *   `Caused by: java.io.IOException: open /data/data/...` →
     *   `Caused by: java.io.IOException`
     *
     *   `	at dev.khoj.pitaka.MainActivity.onCreate(MainActivity.kt:12)` →
     *   unchanged.
     *
     *   A 2nd/3rd line of a multi-line message → dropped entirely.
     */
    internal fun stripMessages(rendered: String): String {
        val out = StringBuilder(rendered.length)
        rendered.split('\n').forEachIndexed { index, raw ->
            val trimmed = raw.trimStart()
            val indented = raw.length != trimmed.length
            when {
                // Blank line — structural, keep.
                trimmed.isEmpty() -> out.append(raw).append('\n')

                // Frame / "... N more" — only when indented, as a real printed
                // trace always indents them. This stops a message line that
                // merely starts with "at " / "... " from masquerading as a frame.
                indented && (trimmed.startsWith("at ") || trimmed.startsWith("... ")) ->
                    out.append(raw).append('\n')

                // Nested exception header — strip the message tail, keep the type.
                trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:") -> {
                    // "<prefix>: <FQN>(: <message>)?" — drop from the 2nd colon on.
                    val firstColon = raw.indexOf(':')
                    val afterPrefix = raw.substring(firstColon + 1)
                    val secondColonRel = afterPrefix.indexOf(':')
                    if (secondColonRel < 0) {
                        out.append(raw)
                    } else {
                        out.append(raw, 0, firstColon + 1 + secondColonRel)
                    }
                    out.append('\n')
                }

                // The very first line is the top-level exception header
                // "<FQN>: <message>" — drop everything from the first colon on.
                index == 0 -> {
                    val colon = raw.indexOf(':')
                    if (colon < 0) out.append(raw) else out.append(raw, 0, colon)
                    out.append('\n')
                }

                // Anything else (a message-continuation line, or an
                // unrecognised shape) is message text — drop it entirely.
                else -> Unit
            }
        }
        return out.toString().trimEnd('\n') + '\n'
    }

    private val ISO_8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
