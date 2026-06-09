package dev.khoj.pitaka.data.import_

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.images.CoverPaths
import javax.inject.Inject

class PitakaJsonImporter @Inject constructor(
    private val moshi: Moshi,
) : Importer {

    @OptIn(ExperimentalStdlibApi::class)
    override fun parse(text: String): ImportPayload = parse(text, keepLocalCovers = false)

    /**
     * @param keepLocalCovers when true, LOCAL `covers/<uuid>.jpg` references are
     *   preserved instead of dropped. Only the bundle (.zip) import path sets
     *   this — it ships the actual cover files alongside the JSON and writes them
     *   into filesDir/covers/ before parsing, so the references resolve. Plain
     *   JSON import keeps the default (false): a JSON file carries no image
     *   bytes, so a local reference would point at a file that does not exist on
     *   this device (PLAN-covers.md D3).
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun parse(text: String, keepLocalCovers: Boolean): ImportPayload {
        val adapter = moshi.adapter<PitakaExport>()
        return try {
            val export = adapter.fromJson(text)
            if (export == null) {
                ImportPayload(emptyList(), emptyList(), listOf("Empty or unparseable JSON."))
            } else if (export.schemaVersion > PitakaExport.SCHEMA_VERSION) {
                ImportPayload(
                    emptyList(),
                    emptyList(),
                    listOf(
                        "This file was created by a newer version of Pitak " +
                                "(schema v${export.schemaVersion}). " +
                                "Update the app before importing."
                    ),
                )
            } else {
                ImportPayload(
                    books = export.books.map {
                        // Fresh ids on import. Drop LOCAL cover references unless
                        // the caller bundled the image files (keepLocalCovers):
                        // (PLAN-covers.md D3) JSON carries no image bytes, and a
                        // local `covers/<uuid>.jpg` / legacy `file://…` path would
                        // point at a file that does not exist on this device — and
                        // historically cross-wired onto a reassigned id (the
                        // original bug). Remote https covers resolve anywhere, so
                        // they pass through untouched in both modes.
                        val cover = if (!keepLocalCovers && CoverPaths.isLocal(it.coverUrl)) null else it.coverUrl
                        it.copy(id = 0L, coverUrl = cover)
                    },
                    wishlist = export.wishlist.map { it.copy(id = 0L) },
                )
            }
        } catch (e: JsonDataException) {
            ImportPayload(emptyList(), emptyList(), listOf("Invalid JSON: ${e.message ?: "unknown"}"))
        } catch (e: java.io.IOException) {
            ImportPayload(emptyList(), emptyList(), listOf("Read error: ${e.message ?: "unknown"}"))
        }
    }
}
