package dev.khoj.pitaka.data.export

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import okio.buffer
import okio.sink
import java.io.OutputStream
import javax.inject.Inject

/**
 * Pure-function exporters. Take a [PitakaExport] (or its constituents),
 * return a String the caller writes to a file via SAF.
 *
 * Each format is independent — no shared rendering pipeline. JSON is the
 * canonical re-importable form; CSV is spreadsheet-friendly.
 */
class Exporters @Inject constructor(
    private val moshi: Moshi,
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun toJson(export: PitakaExport): String {
        val adapter = moshi.adapter<PitakaExport>()
        // Pretty-print for human readability; the byte cost is trivial for
        // personal libraries and the diff-ability matters when the file
        // lives in a git repo (Phase 5 publish path).
        return adapter.indent("  ").toJson(export)
    }

    /**
     * Streams the export directly to [out] via Moshi's okio-backed JsonWriter,
     * so a large library is never materialized as one giant String in memory
     * (P6). The caller owns [out]'s lifecycle (SAF OutputStream); we flush but
     * do not close it. Pretty-printed to match [toJson] for git diff-ability.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun writeJson(export: PitakaExport, out: OutputStream) {
        val adapter = moshi.adapter<PitakaExport>().indent("  ")
        val sink = out.sink().buffer()
        // Don't use .use{} — closing the buffered sink would close the caller's
        // SAF stream; the caller (use {} in the VM) owns that. Flush instead.
        adapter.toJson(sink, export)
        sink.flush()
    }

    fun libraryToCsv(books: List<dev.khoj.pitaka.domain.model.Book>): String = buildString {
        appendLine(LIBRARY_CSV_HEADER)
        books.forEach { b ->
            appendLine(
                listOf(
                    b.id.toString(),
                    b.title,
                    b.titleTransliteration.orEmpty(),
                    b.author.orEmpty(),
                    b.isbn.orEmpty(),
                    b.publisher.orEmpty(),
                    b.publishedYear?.toString().orEmpty(),
                    b.genre.orEmpty(),
                    b.language.orEmpty(),
                    b.pageCount?.toString().orEmpty(),
                    b.coverUrl.orEmpty(),
                    b.notes.orEmpty(),
                    b.location.orEmpty(),
                    b.sourceType?.name.orEmpty(),
                    b.sourceDetail.orEmpty(),
                    b.ageGroup?.token.orEmpty(),
                    b.addedDate.toString(),
                    b.copyCount.toString(),
                ).joinToString(",") { it.csvEscape() }
            )
        }
    }

    fun wishlistToCsv(books: List<dev.khoj.pitaka.domain.model.WishlistBook>): String = buildString {
        appendLine(WISHLIST_CSV_HEADER)
        books.forEach { w ->
            appendLine(
                listOf(
                    w.id.toString(),
                    w.title,
                    w.titleTransliteration.orEmpty(),
                    w.author.orEmpty(),
                    w.isbn.orEmpty(),
                    w.publisher.orEmpty(),
                    w.publishedYear?.toString().orEmpty(),
                    w.coverUrl.orEmpty(),
                    w.priceEstimate?.toString().orEmpty(),
                    w.priority.toString(),
                    w.notes.orEmpty(),
                    w.source.name,
                    w.addedDate.toString(),
                    if (w.purchased) "1" else "0",
                    w.purchasedDate?.toString().orEmpty(),
                ).joinToString(",") { it.csvEscape() }
            )
        }
    }

    companion object {
        const val LIBRARY_CSV_HEADER =
            "id,title,titleTransliteration,author,isbn,publisher,publishedYear,genre,language,pageCount,coverUrl,notes,location,sourceType,sourceDetail,ageGroup,addedDate,copyCount"
        const val WISHLIST_CSV_HEADER =
            "id,title,titleTransliteration,author,isbn,publisher,publishedYear,coverUrl,priceEstimate,priority,notes,source,addedDate,purchased,purchasedDate"
    }
}

// --- escaping helpers ---

/** RFC 4180 minimal CSV escaper. Wraps in quotes only when needed; doubles internal quotes. */
internal fun String.csvEscape(): String {
    val needsQuoting = any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!needsQuoting) return this
    val inner = replace("\"", "\"\"")
    return "\"$inner\""
}
