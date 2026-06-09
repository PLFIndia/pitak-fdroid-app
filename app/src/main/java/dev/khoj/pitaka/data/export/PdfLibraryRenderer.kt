package dev.khoj.pitaka.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import dev.khoj.pitaka.domain.model.Book
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/**
 * Renders a book list to a paginated PDF.
 *
 * Android-only (uses android.graphics.pdf.PdfDocument), so it lives in the data
 * layer rather than the pure [Exporters].
 *
 * Features:
 *  - User-selectable columns (resolved by [PdfColumnPlan]); column widths are
 *    distributed by per-column weight across the printable area.
 *  - Header: optional library logo drawn beside the library name.
 *  - Footer on every page: the Pitak app icon + attribution line.
 *  - Page orientation auto-switches to landscape when many columns are chosen,
 *    so wide selections don't crush the text.
 *  - Multi-line cells (the Source/Source-detail merge renders two lines).
 *
 * Inspired by AOSP's PdfDocument samples and the PrintDocumentAdapter pattern
 * (draw onto each page's Canvas, finishPage, then writeTo the stream).
 *
 * All Android-graphics inputs (logo/footer bitmaps) are injected as nullable
 * [Bitmap]s so the column/layout logic stays unit-testable; the use case loads
 * them from disk/resources and hands them in.
 */
class PdfLibraryRenderer @Inject constructor() {

    /**
     * @param libraryName page-header title.
     * @param logo optional library logo, drawn left of the name (already decoded).
     * @param footerIcon optional Pitak app icon for the footer.
     * @param footerText attribution line drawn beside [footerIcon] on every page.
     * @param columns resolved printable columns (see [PdfColumnPlan.resolve]).
     */
    fun render(
        libraryName: String,
        books: List<Book>,
        columns: List<PdfColumnPlan.PrintColumn>,
        logo: Bitmap?,
        footerIcon: Bitmap?,
        footerText: String,
        out: OutputStream,
    ) {
        val doc = PdfDocument()
        try {
            // Landscape once the selection gets wide, so columns keep breathing room.
            val landscape = columns.size > LANDSCAPE_COLUMN_THRESHOLD
            val pageW = if (landscape) PAGE_LONG else PAGE_SHORT
            val pageH = if (landscape) PAGE_SHORT else PAGE_LONG

            val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
            val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
            val rowPaint = Paint().apply { textSize = BODY_TEXT; isAntiAlias = true }
            val footerPaint = Paint().apply { textSize = FOOTER_TEXT; isAntiAlias = true; color = FOOTER_GREY }
            val footerRulePaint = Paint().apply { isAntiAlias = true; color = FOOTER_GREY; strokeWidth = 0.7f }

            val contentLeft = MARGIN
            val contentRight = pageW - MARGIN
            val contentWidth = contentRight - contentLeft
            val footerTop = pageH - MARGIN - FOOTER_HEIGHT
            val rowBottomLimit = footerTop - 6f

            // A fixed leading serial-number gutter ("#" / 1, 2, 3…). It's a row
            // index, not a book field, so it lives here, not in PdfColumn.
            val serialX = contentLeft
            val columnsLeft = contentLeft + SERIAL_WIDTH
            val columnsWidth = contentRight - columnsLeft

            // Resolve each column's x-offset and pixel width from its weight.
            val totalWeight = columns.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            data class Col(val print: PdfColumnPlan.PrintColumn, val x: Float, val width: Float)
            val cols = buildList {
                var x = columnsLeft
                for (c in columns) {
                    val w = columnsWidth * (c.weight / totalWeight)
                    add(Col(c, x, w))
                    x += w
                }
            }

            // Approx chars that fit a column width at the row text size. At 12pt,
            // an average glyph is ~6.6pt wide, so divide by that to avoid overflow.
            fun maxChars(width: Float): Int = (width / 6.6f).toInt().coerceAtLeast(3)

            var pageNumber = 1
            var page = doc.startPage(pageInfo(pageW, pageH, pageNumber))
            var canvas = page.canvas
            var y: Float

            fun drawFooter(c: Canvas) {
                // Divider rule separating the page body from the footer.
                val ruleY = footerTop
                c.drawLine(contentLeft, ruleY, contentRight, ruleY, footerRulePaint)

                val iconSize = FOOTER_ICON
                val iconY = ruleY + (FOOTER_HEIGHT - iconSize) / 2f + 2f
                var textX = contentLeft
                if (footerIcon != null) {
                    val dst = Rect(
                        contentLeft.toInt(), iconY.toInt(),
                        (contentLeft + iconSize).toInt(), (iconY + iconSize).toInt(),
                    )
                    c.drawBitmap(footerIcon, null, dst, null)
                    textX = contentLeft + iconSize + 8f
                }
                // Vertically centre the footer text against the icon.
                val textY = iconY + iconSize / 2f + FOOTER_TEXT / 2f - 1f
                c.drawText(footerText, textX, textY, footerPaint)
            }

            val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

            fun drawHeader(c: Canvas): Float {
                var top = MARGIN + 4f
                var nameX = contentLeft
                if (logo != null) {
                    // Fit the logo inside a HEADER_LOGO square box preserving aspect
                    // ratio (no stretch), left-aligned and vertically centred.
                    val box = HEADER_LOGO
                    val scale = minOf(box / logo.width, box / logo.height)
                    val w = logo.width * scale
                    val h = logo.height * scale
                    val left = contentLeft
                    val topPad = MARGIN + (box - h) / 2f
                    val dst = RectF(left, topPad, left + w, topPad + h)
                    c.drawBitmap(logo, null, dst, bitmapPaint)
                    nameX = contentLeft + box + 12f
                    top = MARGIN + box * 0.62f // vertically align name to logo
                }
                c.drawText(libraryName, nameX, top, titlePaint)
                return MARGIN + maxOf(HEADER_LOGO, 22f) + 16f
            }

            fun drawColumnHeaders(c: Canvas, startY: Float): Float {
                c.drawText(SERIAL_HEADER, serialX, startY, headerPaint)
                for (col in cols) {
                    c.drawText(ellipsize(col.print.header, maxChars(col.width)), col.x, startY, headerPaint)
                }
                // Underline rule beneath the headers.
                val ruleY = startY + 4f
                c.drawLine(contentLeft, ruleY, contentRight, ruleY, headerPaint)
                return startY + LINE_HEIGHT + 2f
            }

            y = drawHeader(canvas)
            y = drawColumnHeaders(canvas, y)
            drawFooter(canvas)

            if (books.isEmpty()) {
                canvas.drawText("(empty)", contentLeft, y, rowPaint)
            }

            var serial = 0
            for (book in books) {
                serial += 1
                // Pre-compute the wrapped cell lines for this row to know its height.
                val cellLines = cols.map { col ->
                    val limit = maxChars(col.width)
                    val logical = col.print.cell(book)
                    // Word-wrap each logical line to the column width, then cap the
                    // whole cell at the column's wrapLines (hard-truncating beyond).
                    wrapCell(logical, limit, col.print.wrapLines)
                }
                val rowLines = (cellLines.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
                val rowHeight = rowLines * LINE_HEIGHT

                if (y + rowHeight > rowBottomLimit) {
                    doc.finishPage(page)
                    pageNumber += 1
                    page = doc.startPage(pageInfo(pageW, pageH, pageNumber))
                    canvas = page.canvas
                    y = drawHeader(canvas)
                    y = drawColumnHeaders(canvas, y)
                    drawFooter(canvas)
                }

                // Serial number, drawn on the row's first line.
                canvas.drawText("$serial", serialX, y, rowPaint)
                cols.forEachIndexed { i, col ->
                    val lines = cellLines[i]
                    lines.forEachIndexed { lineIdx, line ->
                        canvas.drawText(line, col.x, y + lineIdx * LINE_HEIGHT, rowPaint)
                    }
                }
                y += rowHeight
            }

            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun pageInfo(w: Float, h: Float, number: Int) =
        PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), number).create()

    /** Hard truncate over-long cells so columns don't overlap (no text measuring needed). */
    private fun ellipsize(s: String, max: Int): String =
        if (s.length <= max) s else s.take((max - 1).coerceAtLeast(1)) + "…"

    companion object {
        /**
         * Word-wraps a cell's [logical] lines to fit [maxChars] per line,
         * capping the total at [maxLines]. The last visible line is ellipsised
         * if content remains. Pure (no Android) so it's unit-testable.
         *
         * Each logical line (the merged Source column supplies two) is wrapped
         * independently; their wrapped results are concatenated and then capped,
         * so a two-line source cell with a long detail still respects the cap.
         */
        fun wrapCell(logical: List<String>, maxChars: Int, maxLines: Int): List<String> {
            val limit = maxChars.coerceAtLeast(1)
            val out = mutableListOf<String>()
            for (line in logical) {
                out += wrapWords(line, limit)
            }
            if (out.isEmpty()) return out
            if (out.size <= maxLines) return out
            // Over the cap: keep the first maxLines, ellipsise the last kept one.
            val kept = out.take(maxLines).toMutableList()
            val last = kept.last()
            kept[kept.size - 1] = if (last.length <= limit - 1) "$last…"
            else last.take((limit - 1).coerceAtLeast(1)) + "…"
            return kept
        }

        /** Greedy word-wrap of a single string to [limit] chars/line. A single
         * word longer than [limit] is hard-split. */
        private fun wrapWords(s: String, limit: Int): List<String> {
            val text = s.trim()
            if (text.isEmpty()) return emptyList()
            if (text.length <= limit) return listOf(text)
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in text.split(' ')) {
                var w = word
                // Hard-split a word that can't fit on its own line.
                while (w.length > limit) {
                    if (current.isNotEmpty()) { lines += current.toString(); current = StringBuilder() }
                    lines += w.take(limit)
                    w = w.drop(limit)
                }
                when {
                    current.isEmpty() -> current.append(w)
                    current.length + 1 + w.length <= limit -> current.append(' ').append(w)
                    else -> { lines += current.toString(); current = StringBuilder(w) }
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines
        }

        // A4 at 72dpi, in points. SHORT = portrait width / landscape height.
        const val PAGE_SHORT = 595f
        const val PAGE_LONG = 842f
        const val MARGIN = 36f
        const val BODY_TEXT = 12f       // body/row text size (user spec: 12pt)
        const val FOOTER_TEXT = 9f      // footer text size (user spec: 9pt at 12pt body)
        const val LINE_HEIGHT = 17f     // row line height for 12pt text
        const val HEADER_LOGO = 44f
        const val SERIAL_WIDTH = 30f    // leading "#" serial-number gutter
        const val SERIAL_HEADER = "#"
        const val FOOTER_HEIGHT = 30f   // taller to seat the divider + larger icon
        const val FOOTER_ICON = 22f     // larger, composited saffron app icon
        const val FOOTER_GREY = 0xFF888888.toInt()
        // Beyond this many columns, switch portrait → landscape.
        const val LANDSCAPE_COLUMN_THRESHOLD = 6

        /** Decode the library logo file (a local path saved at pick time) to a Bitmap. */
        fun decodeLogo(path: String): Bitmap? =
            if (path.isBlank()) null
            else runCatching { BitmapFactory.decodeFile(File(path).path) }.getOrNull()
    }
}
