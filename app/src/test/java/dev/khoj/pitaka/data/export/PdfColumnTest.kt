package dev.khoj.pitaka.data.export

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import org.junit.Test

/**
 * Unit tests for the pure PDF column model + the Source-merge rule.
 * No Android — [PdfColumn]/[PdfColumnPlan] are deliberately pure so this runs
 * on the plain JUnit runner.
 */
class PdfColumnTest {

    private val labels = PdfColumnLabels(
        header = PdfColumn.entries.associateWith { it.name.lowercase() },
        sourceType = { t ->
            when (t) {
                Book.SourceType.GIFT -> "Gifted"
                Book.SourceType.PURCHASED -> "Purchased"
                else -> t.name
            }
        },
        ageGroup = { g -> g.name },
        formatDate = { millis -> "date($millis)" },
    )

    private fun book(
        title: String = "T",
        sourceType: Book.SourceType? = null,
        sourceDetail: String? = null,
        location: String? = null,
    ) = Book(title = title, sourceType = sourceType, sourceDetail = sourceDetail, location = location)

    // --- selection / persistence round-trip ------------------------------

    @Test
    fun csv_round_trip_preserves_selection_in_canonical_order() {
        val sel = listOf(PdfColumn.QUANTITY, PdfColumn.TITLE, PdfColumn.AUTHOR)
        val csv = PdfColumn.toCsv(sel)
        val back = PdfColumn.parseCsv(csv)
        // Canonical order: TITLE, AUTHOR, ..., QUANTITY.
        assertThat(back).containsExactly(PdfColumn.TITLE, PdfColumn.AUTHOR, PdfColumn.QUANTITY).inOrder()
    }

    @Test
    fun parse_always_includes_title_even_if_absent() {
        val back = PdfColumn.parseCsv("AUTHOR,ISBN")
        assertThat(back.first()).isEqualTo(PdfColumn.TITLE)
        assertThat(back).containsExactly(PdfColumn.TITLE, PdfColumn.AUTHOR, PdfColumn.ISBN).inOrder()
    }

    @Test
    fun parse_ignores_unknown_tokens() {
        val back = PdfColumn.parseCsv("TITLE,NONSENSE,ISBN")
        assertThat(back).containsExactly(PdfColumn.TITLE, PdfColumn.ISBN).inOrder()
    }

    @Test
    fun private_fields_are_flagged() {
        assertThat(PdfColumn.LOCATION.private).isTrue()
        assertThat(PdfColumn.SOURCE.private).isTrue()
        assertThat(PdfColumn.SOURCE_DETAIL.private).isTrue()
        assertThat(PdfColumn.TITLE.private).isFalse()
    }

    // --- Source-merge rule ----------------------------------------------

    @Test
    fun both_source_columns_merge_into_one_two_line_cell() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.SOURCE, PdfColumn.SOURCE_DETAIL), labels)
        // One merged Source column, not two.
        val sourceCols = plan.filter { it.key == PdfColumn.SOURCE }
        assertThat(sourceCols).hasSize(1)
        assertThat(plan.none { it.key == PdfColumn.SOURCE_DETAIL }).isTrue()

        val cell = sourceCols.single().cell(book(sourceType = Book.SourceType.GIFT, sourceDetail = "Ravi"))
        // Two lines: type then detail.
        assertThat(cell).containsExactly("Gifted", "Ravi").inOrder()
    }

    @Test
    fun merged_source_cell_drops_blank_lines() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.SOURCE, PdfColumn.SOURCE_DETAIL), labels)
        val col = plan.single { it.key == PdfColumn.SOURCE }
        // Only a type, no detail → single line, no trailing blank.
        assertThat(col.cell(book(sourceType = Book.SourceType.GIFT))).containsExactly("Gifted")
        // Neither set → empty cell (no "\n").
        assertThat(col.cell(book())).isEmpty()
    }

    @Test
    fun source_alone_is_a_single_line_column() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.SOURCE), labels)
        val col = plan.single { it.key == PdfColumn.SOURCE }
        assertThat(col.cell(book(sourceType = Book.SourceType.PURCHASED))).containsExactly("Purchased")
    }

    @Test
    fun source_detail_alone_renders_its_own_column() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.SOURCE_DETAIL), labels)
        assertThat(plan.any { it.key == PdfColumn.SOURCE_DETAIL }).isTrue()
        val col = plan.single { it.key == PdfColumn.SOURCE_DETAIL }
        assertThat(col.cell(book(sourceDetail = "Bahrisons"))).containsExactly("Bahrisons")
    }

    @Test
    fun merged_source_column_gets_combined_weight() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.SOURCE, PdfColumn.SOURCE_DETAIL), labels)
        val col = plan.single { it.key == PdfColumn.SOURCE }
        assertThat(col.weight).isEqualTo(PdfColumn.SOURCE.weight + PdfColumn.SOURCE_DETAIL.weight)
    }

    // --- ordinary columns ------------------------------------------------

    @Test
    fun resolve_preserves_canonical_order_and_title_first() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.QUANTITY, PdfColumn.AUTHOR), labels)
        val keys = plan.map { it.key }
        assertThat(keys.first()).isEqualTo(PdfColumn.TITLE)
        assertThat(keys).containsExactly(PdfColumn.TITLE, PdfColumn.AUTHOR, PdfColumn.QUANTITY).inOrder()
    }

    @Test
    fun ordinary_cell_is_single_line_and_blank_when_absent() {
        val plan = PdfColumnPlan.resolve(listOf(PdfColumn.LOCATION), labels)
        val col = plan.single { it.key == PdfColumn.LOCATION }
        assertThat(col.cell(book(location = "Shelf 3"))).containsExactly("Shelf 3")
        assertThat(col.cell(book())).isEmpty()
    }
}
