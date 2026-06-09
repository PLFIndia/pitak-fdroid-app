package dev.khoj.pitaka.domain.model

/**
 * Pure, Android-free validation for a GitHub OAuth App **Client ID** pasted
 * by the user (D13 / §1.1 — the user registers their own OAuth App and
 * supplies its Client ID; Pitaka ships no client secret and no developer
 * infrastructure).
 *
 * F-15 (audit): the pasted value was previously accepted as-is, so a mistyped
 * or stale value produced an opaque "Failed to start device flow" from GitHub
 * only after a network round-trip. This catches the obvious paste mistakes
 * up front and lets the UI show a local, actionable error.
 *
 * Deliberately LOOSE. GitHub has shipped several Client ID shapes over time:
 *   - legacy OAuth Apps: 20 hex characters
 *   - GitHub Apps:        `Iv1.` + 16 hex
 *   - newer App IDs:      `Iv23...` and other prefixes
 * Pinning an exact length/charset would risk rejecting a *valid* future ID,
 * which is worse than the opaque error we're replacing. So we only reject
 * what cannot possibly be a bare Client ID: blank input, embedded
 * whitespace, an obviously-pasted URL, and lengths far outside any plausible
 * ID. GitHub remains the authority on whether the ID actually works.
 */
object GitHubClientId {

    /** Plausible bounds. Legacy IDs are 20 chars; App IDs run a bit longer.
     *  Generous on both ends so we never reject a real ID. */
    private const val MIN_LEN = 8
    private const val MAX_LEN = 64

    /**
     * Returns the trimmed Client ID when [raw] is plausibly a bare ID, or
     * null when it is obviously not one. Trims surrounding whitespace only;
     * the ID itself is preserved as typed (IDs are case-sensitive).
     */
    fun normalizeOrNull(raw: String): String? {
        val v = raw.trim()
        if (v.length < MIN_LEN || v.length > MAX_LEN) return null
        // A bare Client ID never contains internal whitespace.
        if (v.any { it.isWhitespace() }) return null
        // Reject a pasted URL or a "client_id=..." fragment — common mistakes
        // when the user copies from the GitHub settings page or a redirect.
        val lower = v.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        if (v.contains('/') || v.contains('=') || v.contains('?') || v.contains('&')) return null
        // Client IDs are made of ASCII letters, digits, and '.' (the App-ID
        // prefix separator). Anything else is a paste artefact.
        if (!v.all { it.isAsciiAlnum() || it == '.' }) return null
        return v
    }

    fun isValid(raw: String): Boolean = normalizeOrNull(raw) != null

    private fun Char.isAsciiAlnum(): Boolean =
        this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'
}
