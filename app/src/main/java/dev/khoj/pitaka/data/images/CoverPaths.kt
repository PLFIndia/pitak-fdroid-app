package dev.khoj.pitaka.data.images

import java.io.File

/**
 * Single source of truth for how a book cover is referenced.
 *
 * Design (PLAN-covers.md D1): a user-supplied cover is stored on disk as
 * `filesDir/covers/<uuid>.jpg` and the book row's `coverUrl` holds the
 * **relative** reference `covers/<uuid>.jpg`. The filename is a random UUID
 * generated at capture time — it is NOT derived from the book id. This
 * decouples image identity from row identity and from the install location,
 * so covers survive export/import and backup/restore without cross-wiring.
 *
 * Three reference shapes exist in the wild:
 *  - **new relative**: `covers/<uuid>.jpg`  (what we write going forward)
 *  - **legacy absolute**: `file:///data/user/0/<pkg>/files/covers/<id>.jpg`
 *    (pre-D1 rows; the Wave-4 healer rewrites or nulls these)
 *  - **remote**: `https://…` from OpenLibrary / Google Books (not local)
 *
 * This object is pure (no Android Context) so it is trivially unit-testable;
 * the only filesystem touch takes an explicit [File] `filesDir`.
 */
object CoverPaths {
    const val COVERS_DIR = "covers"
    const val PREFIX = "covers/"

    /**
     * True when [coverUrl] points at a cover in our own app storage — either
     * the new relative form or a legacy `file://` absolute form. Remote
     * `http(s)://` covers are NOT local. Blank / null → false.
     */
    fun isLocal(coverUrl: String?): Boolean {
        val s = coverUrl?.trim().orEmpty()
        return when {
            s.isEmpty() -> false
            s.startsWith(PREFIX) -> true
            s.startsWith("file://") -> true
            else -> false
        }
    }

    /** Relative reference for a freshly-captured cover with the given [uuid]. */
    fun relativeFor(uuid: String): String = "$PREFIX$uuid.jpg"

    /**
     * Extracts the validated leaf filename from a local cover reference, or
     * null when [coverUrl] is not a safe local reference.
     *
     * Mirrors the rejection rules in [dev.khoj.pitaka.domain.usecase.CoverUrlAllowList]
     * (no path traversal, no nested directories, no protocol smuggling) so the
     * local-resolve path can never escape the covers/ namespace.
     */
    fun leafOf(coverUrl: String?): String? {
        val s = coverUrl?.trim().orEmpty()
        if (s.isEmpty()) return null
        val leaf = when {
            s.startsWith(PREFIX) -> s.removePrefix(PREFIX)
            s.startsWith("file://") -> s.substringAfterLast('/')
            else -> return null
        }
        if (leaf.isEmpty()) return null
        if (leaf.contains("..")) return null
        if (leaf.contains("/")) return null
        if (leaf.contains(":")) return null
        return leaf
    }

    /**
     * Resolves a local cover reference to an absolute [File] under
     * `filesDir/covers/`, or null when [coverUrl] is not a safe local
     * reference. Does not check existence — callers decide what a missing
     * file means in their context.
     */
    fun absoluteCoverFile(filesDir: File, coverUrl: String?): File? {
        val leaf = leafOf(coverUrl) ?: return null
        return File(File(filesDir, COVERS_DIR), leaf)
    }
}
