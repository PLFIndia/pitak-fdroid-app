package dev.khoj.pitaka.domain.model

/**
 * Pure, Android-free validation for a Pitak **library ID** (PLAN-merge.md D40):
 * the random namespace token that says WHICH library an export file belongs to,
 * so a recipient's merge gate can decide match-vs-decision.
 *
 * Single source of truth for the ID's string limitations. Every path that
 * ADOPTS or PERSISTS a library ID — QR pairing, file merge (Join/Overwrite),
 * regenerate, and the persistence boundary in AppPreferences — funnels through
 * here so a hand-crafted or corrupt export can never inject a malformed ID that
 * would then propagate into every future export and break QR pairing.
 *
 * Shape (matches how we mint them): a library ID is 16–64 characters of
 * lowercase hex `[0-9a-f]`. We mint 32-char hex (16 random bytes via
 * SecureRandom) or a hyphen-stripped UUID — both satisfy this. The bound is
 * deliberately a touch wider than 32 so a future longer token still validates,
 * but tight enough to reject oversized blobs, control characters, uppercase,
 * separators, and prefix-on-junk values.
 */
object LibraryId {

    const val MIN_LEN = 16
    const val MAX_LEN = 64

    /** True when [id] has the shape of a Pitak-minted library ID. */
    fun isValid(id: String): Boolean =
        id.length in MIN_LEN..MAX_LEN && id.all { it in '0'..'9' || it in 'a'..'f' }

    /**
     * Returns [id] trimmed when it is a valid library ID, or null otherwise.
     * Trims surrounding whitespace only; the ID itself is never rewritten (a
     * valid ID has no internal whitespace anyway).
     */
    fun normalizeOrNull(id: String): String? = id.trim().takeIf { isValid(it) }
}
