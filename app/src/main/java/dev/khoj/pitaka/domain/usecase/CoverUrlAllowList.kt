package dev.khoj.pitaka.domain.usecase

import java.net.URI

/**
 * F-09 (audit): sanitise a book's `coverUrl` before publishing it into the
 * world-facing `books.json`.
 *
 * Threat: a poisoned `coverUrl` ("https://attacker.example/track?u=…")
 * silently exfiltrates every visitor of the published page (IP, UA,
 * Referer) on every render. The viewer's HTML escape stops scripted
 * payloads from running but does nothing about cross-origin image
 * requests — those are exactly what an `<img src>` is supposed to do.
 *
 * Policy: allow only
 *   - relative `covers/…` paths produced by the publisher itself, AND
 *   - https:// URLs to a tight host allow-list of book-metadata
 *     providers we already use (OpenLibrary, Google Books).
 *
 * Everything else returns null and the publisher omits the cover; the
 * viewer's `onerror` handler falls back to the inline SVG placeholder.
 *
 * This is the single Kotlin-side source of truth for cover origins. The
 * viewer's CSP `img-src` directive (see `assets/publish/index.html`)
 * mirrors the same host list. If you add or remove a host here, update
 * the CSP in lockstep — there is a snapshot test that fails if either
 * side drifts.
 */
object CoverUrlAllowList {

    /**
     * Hosts allowed for remote cover URLs in published JSON. Case-
     * insensitive match. Subdomains are NOT implicitly allowed — each
     * one must be listed explicitly.
     */
    val ALLOWED_HOSTS: Set<String> = setOf(
        "covers.openlibrary.org",
        "books.google.com",
        "books.googleusercontent.com",
    )

    /**
     * Returns [raw] when it is safe to publish, otherwise null.
     *
     *  - Blank / null → null.
     *  - Relative path starting with `covers/` → returned as-is. These
     *    are produced by the publisher itself (see [PublishCoverIds]).
     *  - https:// URL whose authority hosts an [ALLOWED_HOSTS] entry
     *    and carries no userinfo → returned as-is.
     *  - Anything else → null. This includes http://, data:,
     *    javascript:, file:, about:, URLs with userinfo, URLs with no
     *    host, and URLs to non-allow-listed hosts.
     */
    fun sanitizeCoverUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // Locally-produced bundled covers from the publisher. Reject any
        // attempt to escape that prefix via ".." or absolute paths.
        if (trimmed.startsWith("covers/")) {
            val rest = trimmed.removePrefix("covers/")
            // No path traversal, no nested directories, no protocol smuggling.
            if (rest.isEmpty()) return null
            if (rest.contains("..")) return null
            if (rest.contains("/")) return null
            if (rest.contains(":")) return null
            return trimmed
        }

        // Everything else must parse as an absolute URI.
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in ALLOWED_HOSTS) return null
        // Reject any auth-confusion vector. We never want `https://x@host/…`
        // in our published page, even if `host` itself is allow-listed.
        if (uri.userInfo != null) return null
        return trimmed
    }
}
