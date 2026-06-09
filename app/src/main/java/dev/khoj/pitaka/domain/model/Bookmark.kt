package dev.khoj.pitaka.domain.model

/**
 * A user-saved link to another published library page (GitHub / Cloudflare
 * Pages or a custom domain). Stored as a small JSON array in DataStore — there
 * is no Room table for bookmarks (see BookmarkStore).
 *
 * @param name  user-supplied friendly label (required, non-blank).
 * @param url   the https URL of the library page (validated on entry).
 */
data class Bookmark(
    val name: String,
    val url: String,
)

/**
 * Pure validation/normalisation for bookmark URLs, kept Android-free so it is
 * unit-testable. Decision B: require a well-formed https:// URL with a host;
 * any host is allowed (no github.io / pages.dev gate — custom domains are
 * legitimate). http and malformed input are rejected.
 */
object BookmarkUrl {

    /**
     * Returns a normalised https URL when [raw] is acceptable, or null when it
     * is not. Normalisation: trims surrounding whitespace. The scheme check is
     * case-insensitive; the rest of the URL is preserved as typed.
     */
    fun normalizeOrNull(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        // Must start with https:// (case-insensitive scheme), reject http://.
        val lower = v.lowercase()
        if (!lower.startsWith("https://")) return null
        // Reject embedded whitespace anywhere in the URL.
        if (v.any { it.isWhitespace() }) return null
        // There must be a non-empty host after the scheme, and the host must
        // contain a dot (rules out https://localhost and bare schemes) — a
        // light, forgiving check matching the app's style.
        val afterScheme = v.substring("https://".length)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) return null
        if (!host.contains('.')) return null
        if (host.startsWith('.') || host.endsWith('.')) return null
        return v
    }

    fun isValid(raw: String): Boolean = normalizeOrNull(raw) != null
}
