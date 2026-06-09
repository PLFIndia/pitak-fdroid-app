package dev.khoj.pitaka.data.export

import dev.khoj.pitaka.domain.model.Book

/**
 * The set of book fields the user can choose as columns in the PDF export
 * (feature: user-selectable PDF columns).
 *
 * Pure data layer — no Android, no string resources. Human-readable labels for
 * enum-typed fields (Source, Age group) are injected via [PdfColumnLabels] so
 * the renderer stays testable and the UI owns localisation.
 *
 * `weight` is the relative horizontal share this column takes when laying out
 * the page (Title is widest). `private` marks fields that reveal where/how a
 * book was obtained — selectable (this is the user's own local export) but
 * defaulted OFF so a casually-shared PDF doesn't leak shelf locations.
 */
enum class PdfColumn(
    val weight: Float,
    val private: Boolean = false,
    /** Max lines this column's cell may wrap to (1 = single-line, truncated). */
    val wrapLines: Int = 1,
) {
    TITLE(weight = 3.2f, wrapLines = 3),
    AUTHOR(weight = 2.4f, wrapLines = 2),
    YEAR(weight = 0.9f),
    ISBN(weight = 1.6f),
    PUBLISHER(weight = 2.0f, wrapLines = 2),
    GENRE(weight = 1.4f),
    LANGUAGE(weight = 1.2f),
    PAGES(weight = 0.8f),
    AGE_GROUP(weight = 1.0f),
    QUANTITY(weight = 0.8f),
    ADDED_DATE(weight = 1.3f),
    LOCATION(weight = 2.0f, private = true, wrapLines = 2),
    SOURCE(weight = 1.6f, private = true),
    SOURCE_DETAIL(weight = 1.8f, private = true);

    companion object {
        /** Title is mandatory — a book list with no title column is useless. */
        val MANDATORY = TITLE

        /** Default selection for a first-time / unset export: the classic catalogue. */
        val DEFAULT: List<PdfColumn> = listOf(TITLE, AUTHOR, YEAR, ISBN, QUANTITY)

        /** Stable order columns are laid out left-to-right (declaration order). */
        val ORDER: List<PdfColumn> = entries.toList()

        fun parseCsv(csv: String): List<PdfColumn> {
            val picked = csv.split(',')
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .toSet()
            // Always include Title; preserve canonical left-to-right order.
            val withTitle = picked + MANDATORY
            return ORDER.filter { it in withTitle }
        }

        fun toCsv(columns: Collection<PdfColumn>): String =
            ORDER.filter { it in columns }.joinToString(",") { it.name }
    }
}

/** Localised labels the data-layer renderer needs but cannot resolve itself. */
data class PdfColumnLabels(
    val header: Map<PdfColumn, String>,
    val sourceType: (Book.SourceType) -> String,
    val ageGroup: (Book.AgeGroup) -> String,
    val formatDate: (Long) -> String,
)

/**
 * Resolves the printable columns for a render: the user's selection in
 * canonical order, with the Source-merge rule applied.
 *
 * Source-merge rule (user spec): when BOTH [PdfColumn.SOURCE] and
 * [PdfColumn.SOURCE_DETAIL] are selected, they collapse into a single "Source"
 * column whose cell renders two lines — the type ("Gifted") on line 1, the
 * detail ("friend name") on line 2. When only one of the two is selected it
 * renders as its own single-line column.
 */
object PdfColumnPlan {

    /**
     * One resolved, printable column: a header + a per-book cell (1+ lines).
     * [wrapLines] is the max number of lines the renderer may word-wrap this
     * cell to when its single logical value is wider than the column (1 = no
     * wrap, hard-truncate). The merged Source column pre-splits its own lines
     * and is not word-wrapped, so it carries wrapLines = 1.
     */
    data class PrintColumn(
        val key: PdfColumn,
        val weight: Float,
        val header: String,
        val wrapLines: Int,
        val cell: (Book) -> List<String>,
    )

    fun resolve(selected: List<PdfColumn>, labels: PdfColumnLabels): List<PrintColumn> {
        val set = PdfColumn.parseCsv(PdfColumn.toCsv(selected)).toSet()
        val mergeSource = PdfColumn.SOURCE in set && PdfColumn.SOURCE_DETAIL in set

        val out = mutableListOf<PrintColumn>()
        for (col in PdfColumn.ORDER) {
            if (col !in set) continue
            when {
                col == PdfColumn.SOURCE && mergeSource ->
                    out += PrintColumn(
                        key = PdfColumn.SOURCE,
                        // Merged cell holds two lines — give it both columns' room.
                        weight = PdfColumn.SOURCE.weight + PdfColumn.SOURCE_DETAIL.weight,
                        header = labels.header.getValue(PdfColumn.SOURCE),
                        wrapLines = 1, // pre-split into type/detail lines; no word-wrap
                        cell = { book ->
                            val type = book.sourceType?.let(labels.sourceType).orEmpty()
                            val detail = book.sourceDetail.orEmpty()
                            // Two lines: type, then detail. Drop blanks so an
                            // untouched book renders an empty (not "\n") cell.
                            listOf(type, detail).filter { it.isNotBlank() }
                        },
                    )
                // The detail column is consumed by the merge — skip it.
                col == PdfColumn.SOURCE_DETAIL && mergeSource -> Unit
                else -> out += PrintColumn(
                    key = col,
                    weight = col.weight,
                    header = labels.header.getValue(col),
                    wrapLines = col.wrapLines,
                    cell = { book -> listOf(cellValue(col, book, labels)).filter { it.isNotBlank() } },
                )
            }
        }
        return out
    }

    private fun cellValue(col: PdfColumn, b: Book, labels: PdfColumnLabels): String = when (col) {
        PdfColumn.TITLE -> b.title
        PdfColumn.AUTHOR -> b.author.orEmpty()
        PdfColumn.YEAR -> b.publishedYear?.toString().orEmpty()
        PdfColumn.ISBN -> b.isbn.orEmpty()
        PdfColumn.PUBLISHER -> b.publisher.orEmpty()
        PdfColumn.GENRE -> b.genre.orEmpty()
        PdfColumn.LANGUAGE -> b.language.orEmpty()
        PdfColumn.PAGES -> b.pageCount?.toString().orEmpty()
        PdfColumn.AGE_GROUP -> b.ageGroup?.let(labels.ageGroup).orEmpty()
        PdfColumn.QUANTITY -> b.copyCount.toString()
        PdfColumn.ADDED_DATE -> labels.formatDate(b.addedDate)
        PdfColumn.LOCATION -> b.location.orEmpty()
        PdfColumn.SOURCE -> b.sourceType?.let(labels.sourceType).orEmpty()
        PdfColumn.SOURCE_DETAIL -> b.sourceDetail.orEmpty()
    }
}
