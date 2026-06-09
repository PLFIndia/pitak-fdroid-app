package dev.khoj.pitaka.data.images

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Wave 4 (PLAN-covers.md D4): exhaustive tests for the pure cover-heal
 * planner. The headline test seeds the EXACT corrupt state captured from the
 * user's device and asserts the heal does the right, conservative thing.
 */
class CoverHealPlannerTest {

    // Deterministic UUID supplier so salvage renames are assertable.
    private fun seqUuids(vararg ids: String): () -> String {
        val it = ids.iterator()
        return { it.next() }
    }

    private fun legacy(n: Int) =
        "file:///data/user/0/dev.khoj.pitaka.debug/files/covers/$n.jpg"

    // --- the device fixture ----------------------------------------------

    @Test
    fun heals_the_exact_device_corruption() {
        // Captured 2026-06-01 from device 4C241XEKB6VC38 (see PLAN-covers.md).
        // Files actually present on disk at capture: 6.jpg, 7.jpg, 10.jpg.
        val inputs = listOf(
            CoverHealPlanner.Input(1, legacy(10)),  // present, shared w/ 10  → clear
            CoverHealPlanner.Input(2, legacy(9)),   // missing                → clear
            CoverHealPlanner.Input(3, legacy(8)),   // missing                → clear
            CoverHealPlanner.Input(4, null),        // no cover                → untouched
            CoverHealPlanner.Input(5, legacy(6)),   // present, unique         → salvage
            CoverHealPlanner.Input(6, legacy(5)),   // missing                → clear
            CoverHealPlanner.Input(7, legacy(7)),   // present, unique         → salvage
            CoverHealPlanner.Input(8, "https://covers.openlibrary.org/b/id/1188894-M.jpg"),
            CoverHealPlanner.Input(9, "https://covers.openlibrary.org/b/id/10194609-M.jpg"),
            CoverHealPlanner.Input(10, legacy(10)), // present, shared w/ 1   → clear
        )
        val existing = setOf("6.jpg", "7.jpg", "10.jpg")

        val plan = CoverHealPlanner.plan(inputs, existing, seqUuids("uuidA", "uuidB"))

        // Rows 1,2,3,6,10 cleared. Rows 4,8,9 untouched (null/remote). 5,7 salvaged.
        assertThat(plan.rowActions).containsExactly(
            CoverHealPlanner.RowAction.Clear(1),
            CoverHealPlanner.RowAction.Clear(2),
            CoverHealPlanner.RowAction.Clear(3),
            CoverHealPlanner.RowAction.Rewrite(5, "covers/uuidA.jpg"),
            CoverHealPlanner.RowAction.Clear(6),
            CoverHealPlanner.RowAction.Rewrite(7, "covers/uuidB.jpg"),
            CoverHealPlanner.RowAction.Clear(10),
        )
        assertThat(plan.fileRenames).containsExactly(
            CoverHealPlanner.FileRename("6.jpg", "uuidA.jpg"),
            CoverHealPlanner.FileRename("7.jpg", "uuidB.jpg"),
        )
        // 10.jpg was shared by two now-cleared rows → orphaned → reclaimed.
        assertThat(plan.deleteLeaves).containsExactly("10.jpg")
    }

    // --- focused cases ----------------------------------------------------

    @Test
    fun missing_file_is_cleared() {
        val plan = CoverHealPlanner.plan(
            listOf(CoverHealPlanner.Input(1, legacy(5))),
            existingLeaves = emptySet(),
            newUuid = seqUuids(),
        )
        assertThat(plan.rowActions).containsExactly(CoverHealPlanner.RowAction.Clear(1))
        assertThat(plan.fileRenames).isEmpty()
    }

    @Test
    fun shared_present_file_clears_all_referrers_and_deletes_orphan() {
        val plan = CoverHealPlanner.plan(
            listOf(
                CoverHealPlanner.Input(1, legacy(10)),
                CoverHealPlanner.Input(2, legacy(10)),
            ),
            existingLeaves = setOf("10.jpg"),
            newUuid = seqUuids(),
        )
        assertThat(plan.rowActions).containsExactly(
            CoverHealPlanner.RowAction.Clear(1),
            CoverHealPlanner.RowAction.Clear(2),
        )
        assertThat(plan.deleteLeaves).containsExactly("10.jpg")
    }

    @Test
    fun unique_present_legacy_file_is_salvaged_to_uuid() {
        val plan = CoverHealPlanner.plan(
            listOf(CoverHealPlanner.Input(7, legacy(7))),
            existingLeaves = setOf("7.jpg"),
            newUuid = seqUuids("fresh"),
        )
        assertThat(plan.rowActions)
            .containsExactly(CoverHealPlanner.RowAction.Rewrite(7, "covers/fresh.jpg"))
        assertThat(plan.fileRenames)
            .containsExactly(CoverHealPlanner.FileRename("7.jpg", "fresh.jpg"))
        assertThat(plan.deleteLeaves).isEmpty()
    }

    @Test
    fun already_relative_present_unique_cover_is_left_untouched() {
        val plan = CoverHealPlanner.plan(
            listOf(CoverHealPlanner.Input(1, "covers/good-uuid.jpg")),
            existingLeaves = setOf("good-uuid.jpg"),
            newUuid = seqUuids(),
        )
        // No row action, no rename, and the file is NOT deleted (still referenced).
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun remote_and_null_covers_are_never_touched() {
        val plan = CoverHealPlanner.plan(
            listOf(
                CoverHealPlanner.Input(1, "https://covers.openlibrary.org/b/id/1-M.jpg"),
                CoverHealPlanner.Input(2, null),
            ),
            existingLeaves = emptySet(),
            newUuid = seqUuids(),
        )
        assertThat(plan.rowActions).isEmpty()
    }

    @Test
    fun unsafe_reference_is_cleared() {
        val plan = CoverHealPlanner.plan(
            listOf(CoverHealPlanner.Input(1, "covers/../../etc/passwd")),
            existingLeaves = emptySet(),
            newUuid = seqUuids(),
        )
        assertThat(plan.rowActions).containsExactly(CoverHealPlanner.RowAction.Clear(1))
    }

    @Test
    fun orphan_file_with_no_referrers_is_deleted() {
        val plan = CoverHealPlanner.plan(
            inputs = emptyList(),
            existingLeaves = setOf("stray.jpg"),
            newUuid = seqUuids(),
        )
        assertThat(plan.deleteLeaves).containsExactly("stray.jpg")
        assertThat(plan.rowActions).isEmpty()
    }

    @Test
    fun clean_library_produces_empty_plan() {
        val plan = CoverHealPlanner.plan(
            listOf(
                CoverHealPlanner.Input(1, "covers/a.jpg"),
                CoverHealPlanner.Input(2, null),
                CoverHealPlanner.Input(3, "https://books.google.com/x?id=1"),
            ),
            existingLeaves = setOf("a.jpg"),
            newUuid = seqUuids(),
        )
        assertThat(plan.isEmpty).isTrue()
    }
}
