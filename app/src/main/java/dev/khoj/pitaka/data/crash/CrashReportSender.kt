package dev.khoj.pitaka.data.crash

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import dev.khoj.pitaka.BuildConfig
import dev.khoj.pitaka.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges a stored [CrashReportFile] to a GitHub Issue Form prefill URL on
 * the public crash-log repo, plus a clipboard copy so the user can paste
 * the full trace as a follow-up comment after the issue opens.
 *
 * This class is the **data-layer** side of submission: it produces the URL,
 * copies the trace to the clipboard, and deletes / reads the underlying
 * file. It does NOT open a browser — that is a UI-layer concern handled by
 * `ui.contribute.openInBrowser`, the same helper the translation flow uses.
 * Keeps `data/` free of any dependency on `ui/`.
 *
 * Why a prefilled URL instead of an HTTP POST: §1.1 — no developer
 * infrastructure post-install. We never authenticate against api.github.com
 * on the user's behalf. The user opens the form in their own browser,
 * signs in to GitHub themselves, and clicks Submit.
 *
 * URL length: GitHub Issue Forms over `?title=&body=` work up to roughly
 * 8 KB of URL-encoded content. A full Pitak stack trace can encode larger
 * than that. We therefore embed only the metadata header plus the first
 * [TOP_FRAMES] stack frames in the URL, and tell the user the full trace
 * is on their clipboard. The full trace also remains on disk via
 * [CrashReportStore] until the user explicitly deletes it.
 */
@Singleton
class CrashReportSender(
    private val store: CrashReportStore,
    // F-13: the auto-clear timer runs on an injectable scope and uses an
    // injectable delay so tests can drive it deterministically. Production
    // uses an app-scoped SupervisorJob (matching VaultAutoLocker /
    // AppLockAutoLocker) and a 60s clear window (Bitwarden's default).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clearDelayMs: Long = DEFAULT_CLEAR_DELAY_MS,
) {

    @Inject
    constructor(store: CrashReportStore) : this(
        store = store,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        clearDelayMs = DEFAULT_CLEAR_DELAY_MS,
    )

    /** Tracks the pending auto-clear so a fresh copy supersedes an old timer. */
    private var clearJob: Job? = null

    /** A submission payload prepared for the UI layer to hand to a browser. */
    data class SubmissionPayload(
        /** The prefilled GitHub Issue URL. Open this in a browser. */
        val url: String,
        /** Length of the URL — useful for debugging the 8 KB ceiling. */
        val urlLength: Int = url.length,
    )

    /**
     * Prepare a submission for [report]:
     *   1. Read the trace from disk.
     *   2. Copy the full trace to the clipboard so the user can paste it as
     *      a follow-up comment after submitting.
     *   3. Build the prefilled GitHub Issue URL with the first [TOP_FRAMES]
     *      frames inline.
     *
     * Returns `null` if the file disappeared / unreadable before we got to
     * it. Callers should surface a toast and refresh the list. Does NOT
     * delete the file — that is the user's explicit action via [delete] in
     * case the browser tab never reaches GitHub or they decide not to send.
     */
    fun prepare(ctx: Context, report: CrashReportFile): SubmissionPayload? {
        val body = store.read(report.file) ?: return null
        copyToClipboard(ctx, body)
        // F-17: repo slug from string resources (single source of truth with
        // the translations repo). If it's blank the URL build returns null and
        // the caller falls back to the copied-to-clipboard path — no 404.
        val owner = ctx.getString(R.string.crash_repo_owner)
        val repo = ctx.getString(R.string.crash_repo_name)
        val url = buildIssueUrl(body, owner, repo) ?: return null
        return SubmissionPayload(url = url)
    }

    /** "Copy full trace to clipboard" — sidekick action on the Settings list. */
    fun copy(ctx: Context, report: CrashReportFile): Boolean {
        val body = store.read(report.file) ?: return false
        copyToClipboard(ctx, body)
        return true
    }

    fun delete(report: CrashReportFile): Boolean = store.delete(report.file)

    fun deleteAll() = store.deleteAll()

    private fun copyToClipboard(ctx: Context, body: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return
            val clip = ClipData.newPlainText(CLIP_LABEL, body).apply {
                // API 33+: mark the clip sensitive so the system clipboard
                // preview (the toast/overlay that shows what was copied) hides
                // the contents. No-op below 33.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }
            cm.setPrimaryClip(clip)
            scheduleAutoClear(cm)
        }
    }

    /**
     * F-13: schedule a one-shot clear of the clipboard [clearDelayMs] after a
     * crash trace was copied. The clear is conditional — it only wipes the
     * clipboard if our trace is *still* the primary clip (matched by label),
     * so we never destroy something the user copied in the meantime.
     *
     * A new copy cancels any pending clear and starts a fresh timer, so the
     * window is always measured from the most recent copy.
     */
    private fun scheduleAutoClear(cm: ClipboardManager) {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearDelayMs)
            clearIfStillOurs(cm)
        }
    }

    private fun clearIfStillOurs(cm: ClipboardManager) {
        runCatching {
            val current = cm.primaryClip ?: return
            // Only clear if the clip on the board is still our crash trace.
            if (current.description?.label != CLIP_LABEL) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                // API 26–27: no clearPrimaryClip(). Overwrite with an empty
                // clip so the trace no longer sits on the board.
                cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, ""))
            }
        }
    }

    /**
     * Build the prefilled GitHub Issue URL. Form-field names here mirror
     * `id:` entries in the `crash.yml` Issue Form in the crash-log repo.
     *
     * [owner] / [repo] are passed in from the string resources (F-17) rather
     * than read from BuildConfig, so both developer repos are configured in
     * one place. Returns `null` if the body is unusable OR if owner/repo is
     * blank — the caller then falls back to copy-to-clipboard instead of
     * opening a 404 page.
     */
    private fun buildIssueUrl(body: String, owner: String, repo: String): String? {
        if (body.isBlank()) return null
        if (owner.isBlank() || repo.isBlank()) return null
        val (header, frames) = splitHeaderAndFrames(body)
        val title = buildTitle(body)
        val composed = buildString {
            append(header)
            append("\n\nFirst ").append(TOP_FRAMES)
                .append(" stack frames (full trace was copied to your clipboard — paste as a comment after submitting):\n\n")
            append("```\n")
            append(frames)
            append("\n```\n")
        }
        val params = buildString {
            append("template=crash.yml")
            append("&labels=").append(Uri.encode("crash"))
            append("&title=").append(Uri.encode(title))
            append("&pitak-version=").append(Uri.encode(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"))
            append("&summary=").append(Uri.encode(composed))
        }
        return "https://github.com/$owner/$repo/issues/new?$params"
    }

    /**
     * Pull the metadata block (everything up to the first blank line) and
     * the stack-frame portion (after the blank line), trimming the frame
     * portion to the top [TOP_FRAMES] frames so the URL stays under
     * GitHub's prefill ceiling.
     */
    private fun splitHeaderAndFrames(body: String): Pair<String, String> {
        val blank = body.indexOf("\n\n")
        if (blank < 0) return body to ""
        val header = body.substring(0, blank)
        val rest = body.substring(blank + 2)
        val out = ArrayList<String>(TOP_FRAMES + 4)
        var framesKept = 0
        for (line in rest.lines()) {
            if (line.trimStart().startsWith("at ")) {
                if (framesKept >= TOP_FRAMES) {
                    out += "\t... (truncated; full trace is in your clipboard)"
                    break
                }
                out += line
                framesKept++
            } else {
                out += line
            }
        }
        return header to out.joinToString("\n").trimEnd()
    }

    /**
     * Issue title: `Crash: <ExceptionType> in <topFrameMethod>`. Falls back
     * to "Unknown" / "unknown" if the body shape is unexpected so we never
     * crash inside the crash submitter.
     */
    private fun buildTitle(body: String): String {
        val lines = body.lines()
        // Header line of the exception sits after the metadata block, on its
        // own line, no leading whitespace, no spaces, and we already stripped
        // messages so no trailing ": ...".
        val typeLine = lines.firstOrNull { line ->
            val l = line.trim()
            l.isNotEmpty() &&
                line.first() != '\t' &&
                line.first() != ' ' &&
                !l.startsWith("Pitak ") &&
                !l.contains(' ') &&
                !l.contains('=') &&
                l.contains('.') &&
                !l.contains(':')
        } ?: "Unknown"
        val topFrame = lines
            .firstOrNull { it.trimStart().startsWith("at ") }
            ?.let { line ->
                val openParen = line.indexOf('(')
                val atIdx = line.indexOf("at ")
                if (atIdx < 0) null
                else if (openParen < 0) line.substring(atIdx + 3).trim()
                else line.substring(atIdx + 3, openParen).trim()
            } ?: "unknown"
        val shortType = typeLine.substringAfterLast('.')
        return "Crash: $shortType in $topFrame"
    }

    companion object {
        /** How many top stack frames to include in the URL-prefilled body. */
        const val TOP_FRAMES = 20

        /** Clip label; also the match key for the conditional auto-clear. */
        const val CLIP_LABEL = "Pitak crash report"

        /** F-13: default auto-clear window (60s), matching Bitwarden Android. */
        const val DEFAULT_CLEAR_DELAY_MS = 60_000L
    }
}
