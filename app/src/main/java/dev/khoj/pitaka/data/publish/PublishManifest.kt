package dev.khoj.pitaka.data.publish

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device record of what we last published, so the next publish can skip
 * files whose content is unchanged (incremental publish).
 *
 * Stored as plain JSON in `filesDir/publish_manifest.json` (Q4=A): it holds only
 * PUBLIC data — repo file paths, git blob SHAs, and public cover URLs — so it
 * needs no encryption. It is a CACHE, never the source of truth: if it is
 * missing or stale, the publish flow rebuilds it from the repo's real git tree
 * (the fresh-install safety net), so a wrong/empty manifest can only ever cause
 * a redundant upload, never a missing or wrong file on the page.
 *
 * Keyed to the target repo: publishing to a different repo must not reuse the
 * previous repo's shas. [repo] is "owner/name".
 */
@JsonClass(generateAdapter = false)
data class PublishManifest(
    /** "owner/name" this manifest describes; null/blank in the empty manifest. */
    val repo: String? = null,
    /** Published file path -> git blob sha last uploaded for it. */
    val fileShas: Map<String, String> = emptyMap(),
    /**
     * Book id -> the `coverUrl` value at last publish. Drives the Q2-C
     * source-identity skip for REMOTE covers: if a book's coverUrl is unchanged
     * and we still have a sha for its cover path, we skip fetch+downscale+upload
     * entirely.
     */
    val coverUrlByBookId: Map<String, String> = emptyMap(),
) {
    fun shaFor(path: String): String? = fileShas[path]

    companion object {
        val EMPTY = PublishManifest()
    }
}

/**
 * Reads/writes [PublishManifest] to `filesDir/publish_manifest.json`.
 * All failures degrade to [PublishManifest.EMPTY] (→ a full, correct publish),
 * never a crash — the manifest is an optimization, not a correctness input.
 */
@Singleton
class PublishManifestStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    @OptIn(ExperimentalStdlibApi::class)
    fun load(): PublishManifest =
        runCatching {
            if (!file.exists()) return PublishManifest.EMPTY
            val json = file.readText(Charsets.UTF_8)
            moshi.adapter<PublishManifest>().fromJson(json) ?: PublishManifest.EMPTY
        }.getOrDefault(PublishManifest.EMPTY)

    @OptIn(ExperimentalStdlibApi::class)
    fun save(manifest: PublishManifest) {
        runCatching {
            val json = moshi.adapter<PublishManifest>().toJson(manifest)
            file.writeText(json, Charsets.UTF_8)
        }
        // Best-effort: a failed manifest write just means the next publish does
        // more work, not that anything is wrong on the page.
    }

    /** Clears the manifest (e.g. on sign-out / target-repo change if desired). */
    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        const val FILE_NAME = "publish_manifest.json"
    }
}
