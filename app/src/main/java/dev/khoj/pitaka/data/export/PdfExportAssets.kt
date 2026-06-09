package dev.khoj.pitaka.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Book
import javax.inject.Inject

/**
 * Android-side provider for the PDF export's localised labels and bitmap
 * assets (the Pitak footer icon, the column headers, enum value labels).
 *
 * Kept out of [ExportUseCase] so the use case stays Android-light and the
 * pure column/layout logic ([PdfColumn], [PdfColumnPlan]) stays unit-testable.
 */
class PdfExportAssets @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun labels(): PdfColumnLabels = PdfColumnLabels(
        header = mapOf(
            PdfColumn.TITLE to context.getString(R.string.pdf_col_title),
            PdfColumn.AUTHOR to context.getString(R.string.pdf_col_author),
            PdfColumn.YEAR to context.getString(R.string.pdf_col_year),
            PdfColumn.ISBN to context.getString(R.string.pdf_col_isbn),
            PdfColumn.PUBLISHER to context.getString(R.string.pdf_col_publisher),
            PdfColumn.GENRE to context.getString(R.string.pdf_col_genre),
            PdfColumn.LANGUAGE to context.getString(R.string.pdf_col_language),
            PdfColumn.PAGES to context.getString(R.string.pdf_col_pages),
            PdfColumn.AGE_GROUP to context.getString(R.string.pdf_col_age_group),
            PdfColumn.QUANTITY to context.getString(R.string.pdf_col_quantity),
            PdfColumn.ADDED_DATE to context.getString(R.string.pdf_col_added_date),
            PdfColumn.LOCATION to context.getString(R.string.pdf_col_location),
            PdfColumn.SOURCE to context.getString(R.string.pdf_col_source),
            PdfColumn.SOURCE_DETAIL to context.getString(R.string.pdf_col_source_detail),
        ),
        sourceType = { type -> context.getString(sourceTypeRes(type)) },
        ageGroup = { group -> context.getString(ageGroupRes(group)) },
        formatDate = { millis ->
            java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(millis))
        },
    )

    val footerText: String get() = context.getString(R.string.pdf_footer_attribution)

    /**
     * The Pitak app icon as a bitmap for the PDF footer.
     *
     * Renders the ACTUAL launcher drawable (`mipmap/ic_launcher`, the adaptive
     * icon) so the footer icon matches the real app icon's proportions and
     * composition (saffron background + foreground) exactly — rather than
     * hand-rebuilding it, which drifted from the launcher. An adaptive icon
     * drawn to a bitmap composites its background + foreground into a full
     * square (no launcher mask), which is what we want on a page.
     */
    fun footerIcon(sizePx: Int = 96): Bitmap? = runCatching {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?: return@runCatching null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        bmp
    }.getOrNull()

    private fun sourceTypeRes(type: Book.SourceType): Int = when (type) {
        Book.SourceType.PURCHASED -> R.string.source_type_purchased
        Book.SourceType.GIFT -> R.string.source_type_gift
        Book.SourceType.DONATED -> R.string.source_type_donated
        Book.SourceType.INHERITED -> R.string.source_type_inherited
        Book.SourceType.OTHER -> R.string.source_type_other
    }

    private fun ageGroupRes(group: Book.AgeGroup): Int = when (group) {
        Book.AgeGroup.ABOVE_3 -> R.string.age_group_above_3
        Book.AgeGroup.ABOVE_6 -> R.string.age_group_above_6
        Book.AgeGroup.ABOVE_10 -> R.string.age_group_above_10
        Book.AgeGroup.ABOVE_15 -> R.string.age_group_above_15
        Book.AgeGroup.ADVANCED -> R.string.age_group_advanced
    }
}
