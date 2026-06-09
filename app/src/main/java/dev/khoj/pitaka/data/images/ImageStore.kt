package dev.khoj.pitaka.data.images

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-private storage for user-supplied cover images and the
 * library logo (Phase 8).
 *
 * Files live under `context.filesDir/covers/` and `context.filesDir/logo.png`.
 *
 * Cover identity (PLAN-covers.md D1): each captured cover gets a random UUID
 * filename `covers/<uuid>.jpg`, and the Book row stores the **relative**
 * reference `covers/<uuid>.jpg` (see [CoverPaths]). The filename is NOT the
 * book id, so covers no longer cross-wire when a row's id changes (import,
 * restore). Coil resolves the relative reference to an absolute path at render
 * time; the publish pipeline resolves it against `filesDir`.
 */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: ImagePipeline,
) {

    /**
     * Imports a user-picked / scanned SAF Uri as a cover.
     *
     * Pipeline: SAF input → downscale to 400×600 JPEG q80 → save to
     * app-private `covers/<uuid>.jpg`. Returns the **relative**
     * `covers/<uuid>.jpg` reference the Book row should store in `cover_url`,
     * or null if the source could not be decoded.
     *
     * The caller is responsible for deleting any previous cover file for the
     * book (via [deleteCoverByUrl]) — this method only ever creates a new file
     * under a fresh UUID, so it never overwrites or collides with another row.
     */
    fun importBookCover(uri: Uri): String? {
        val coversDir = File(context.filesDir, CoverPaths.COVERS_DIR).apply { mkdirs() }
        val uuid = UUID.randomUUID().toString()
        val target = File(coversDir, "$uuid.jpg")
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            pipeline.downscaleForPublish(it)
        } ?: return null
        target.writeBytes(bytes)
        return CoverPaths.relativeFor(uuid)
    }

    /**
     * Imports a user-picked SAF Uri as the library logo (Settings →
     * Appearance). Center-cropped to a 256×256 PNG so it sits cleanly
     * next to the title in the top app bar.
     */
    fun importLibraryLogo(uri: Uri): String? {
        val target = File(context.filesDir, LOGO_FILE)
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            pipeline.centerCropSquare(it)
        } ?: return null
        target.writeBytes(bytes)
        return Uri.fromFile(target).toString()
    }

    fun clearLibraryLogo() {
        File(context.filesDir, LOGO_FILE).delete()
    }

    /**
     * Deletes the on-disk file backing a local cover reference, if any.
     * Accepts both the new relative `covers/<uuid>.jpg` form and legacy
     * `file://…/covers/<id>.jpg` paths. Remote URLs and null are no-ops.
     * Returns true if a file was actually deleted.
     */
    fun deleteCoverByUrl(coverUrl: String?): Boolean {
        val f = CoverPaths.absoluteCoverFile(context.filesDir, coverUrl) ?: return false
        return f.exists() && f.delete()
    }

    fun libraryLogoFile(): File? {
        val f = File(context.filesDir, LOGO_FILE)
        return if (f.exists()) f else null
    }

    /**
     * Resolves a local cover reference to its on-disk [File] if it exists.
     * Returns null for remote URLs, unsafe references, or missing files.
     */
    fun coverFileForUrl(coverUrl: String?): File? {
        val f = CoverPaths.absoluteCoverFile(context.filesDir, coverUrl) ?: return null
        return if (f.exists()) f else null
    }

    companion object {
        const val LOGO_FILE = "library_logo.png"
    }
}
