package dev.khoj.pitaka.domain.model

/**
 * Pure structural validation for ISBN strings, kept Android-free so it is
 * unit-testable. Mirrors the [BookmarkUrl] pattern.
 *
 * F-16 (audit): ISBN strings are interpolated into the Open Library cover
 * CDN path (`covers.openlibrary.org/b/isbn/<isbn>-L.jpg`). OkHttp
 * canonicalises the URL so a malformed ISBN cannot escape the authority —
 * this is hygiene, not a host-hijack defence. The win is that we no longer
 * fire a doomed HEAD request for a typo'd or non-ISBN string.
 *
 * Scope: this validates only the *normalised* form produced by
 * `LookupIsbnUseCase.normalize()` (dashes/spaces stripped, uppercased). It
 * checks structure (length + character class + check digit), not whether the
 * ISBN actually exists. A structurally-valid ISBN that no provider knows is
 * still a legitimate lookup; this gate only rules out garbage.
 */
object IsbnFormat {

    /**
     * True when [normalized] is a structurally valid ISBN-10 or ISBN-13.
     * Expects the already-normalised form (no dashes/spaces, uppercase).
     * Validates the check digit so transposition typos are caught cheaply.
     */
    fun isValid(normalized: String): Boolean =
        isValidIsbn13(normalized) || isValidIsbn10(normalized)

    private fun isValidIsbn13(s: String): Boolean {
        if (s.length != 13) return false
        if (!s.all { it in '0'..'9' }) return false
        // ISBN-13 check digit: weighted 1,3,1,3,... sum ≡ 0 (mod 10).
        var sum = 0
        for (i in 0 until 12) {
            val d = s[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        val check = (10 - (sum % 10)) % 10
        return check == (s[12] - '0')
    }

    private fun isValidIsbn10(s: String): Boolean {
        if (s.length != 10) return false
        // First 9 are digits; the 10th may be 'X' (value 10).
        var sum = 0
        for (i in 0 until 9) {
            val c = s[i]
            if (c !in '0'..'9') return false
            sum += (c - '0') * (10 - i)
        }
        val last = s[9]
        val checkVal = when {
            last in '0'..'9' -> last - '0'
            last == 'X' -> 10
            else -> return false
        }
        sum += checkVal
        return sum % 11 == 0
    }
}
