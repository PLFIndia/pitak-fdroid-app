package dev.khoj.pitaka.domain.merge

import com.google.common.truth.Truth.assertThat
import dev.khoj.pitaka.domain.model.Book
import org.junit.Test

/**
 * Exhaustive tests for the pure [LibraryMergeEngine] (PLAN-merge.md S3).
 *
 * Covers: uid identity (identical no-op / conflict), ISBN identity, add-new,
 * no-ISBN fuzzy possible-duplicate, removal-only conflict, no fan-in onto one
 * local row, idempotency (re-merging the same file is a no-op), and the
 * normalisation helpers.
 */
class LibraryMergeEngineTest {

    private fun book(
        id: Long = 0L,
        uid: String? = null,
        title: String = "Title",
        author: String? = null,
        isbn: String? = null,
        genre: String? = null,
        removed: Boolean = false,
        copyCount: Int = 1,
    ) = Book(
        id = id,
        bookUid = uid,
        title = title,
        author = author,
        isbn = isbn,
        genre = genre,
        addedDate = 1000L,
        copyCount = copyCount,
        removed = removed,
    )

    // ---- uid identity ----

    @Test
    fun uid_match_identical_is_noop() {
        val local = listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111"))
        val incoming = listOf(book(id = 99, uid = "u1", title = "Godaan", isbn = "111"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.identical).isEqualTo(1)
        assertThat(plan.toAdd).isEmpty()
        assertThat(plan.conflicts).isEmpty()
        assertThat(plan.isNoOp).isTrue()
    }

    @Test
    fun uid_match_differing_field_is_conflict() {
        val local = listOf(book(id = 1, uid = "u1", title = "Godaan", genre = "Fiction"))
        val incoming = listOf(book(id = 99, uid = "u1", title = "Godaan", genre = "Classic"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.conflicts).hasSize(1)
        assertThat(plan.conflicts[0].matchedBy).isEqualTo(LibraryMergeEngine.MatchKind.UID)
        assertThat(plan.toAdd).isEmpty()
        assertThat(plan.identical).isEqualTo(0)
    }

    // ---- ISBN identity ----

    @Test
    fun isbn_match_when_no_uid_match_identical_is_noop() {
        // Same physical book scanned on two phones: different uids, same ISBN.
        val local = listOf(book(id = 1, uid = "uA", title = "Sapiens", isbn = "978-0-00-1"))
        val incoming = listOf(book(id = 2, uid = "uB", title = "Sapiens", isbn = "9780001"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        // ISBN normalises equal → matched; titles equal → identical.
        assertThat(plan.identical).isEqualTo(1)
        assertThat(plan.conflicts).isEmpty()
        assertThat(plan.toAdd).isEmpty()
    }

    @Test
    fun isbn_match_differing_field_is_conflict_matched_by_isbn() {
        val local = listOf(book(id = 1, uid = "uA", title = "Sapiens", isbn = "9780001", genre = "History"))
        val incoming = listOf(book(id = 2, uid = "uB", title = "Sapiens", isbn = "9780001", genre = "Anthropology"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.conflicts).hasSize(1)
        assertThat(plan.conflicts[0].matchedBy).isEqualTo(LibraryMergeEngine.MatchKind.ISBN)
    }

    // ---- add-new ----

    @Test
    fun incoming_with_isbn_and_no_match_is_added() {
        val local = listOf(book(id = 1, uid = "u1", title = "Godaan", isbn = "111"))
        val incoming = listOf(book(id = 5, uid = "u2", title = "Nineteen Eighty-Four", isbn = "222"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.toAdd.map { it.title }).containsExactly("Nineteen Eighty-Four")
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun incoming_no_isbn_no_similar_local_is_added() {
        val local = listOf(book(id = 1, title = "Completely Different Book", author = "X"))
        val incoming = listOf(book(id = 5, uid = "u2", title = "Kabir Ke Dohe", author = "Kabir"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.toAdd.map { it.title }).containsExactly("Kabir Ke Dohe")
        assertThat(plan.possibleDuplicates).isEmpty()
    }

    // ---- no-ISBN fuzzy ----

    @Test
    fun no_isbn_close_title_author_is_possible_duplicate_not_added() {
        // Two maintainers independently typed the same regional book, no ISBN,
        // different uids → must be surfaced, not silently doubled.
        val local = listOf(book(id = 1, uid = "uA", title = "Kabir Ke Dohe", author = "Kabir Das"))
        val incoming = listOf(book(id = 2, uid = "uB", title = "Kabir ke Dohe", author = "Kabir Das"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.possibleDuplicates).hasSize(1)
        assertThat(plan.possibleDuplicates[0].similarity).isAtLeast(LibraryMergeEngine.DEFAULT_FUZZY_THRESHOLD)
        assertThat(plan.toAdd).isEmpty()
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun no_isbn_weak_similarity_is_added_not_surfaced() {
        val local = listOf(book(id = 1, title = "Kabir Ke Dohe", author = "Kabir"))
        val incoming = listOf(book(id = 2, uid = "uB", title = "Tulsi Ramayan", author = "Tulsidas"))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.possibleDuplicates).isEmpty()
        assertThat(plan.toAdd.map { it.title }).containsExactly("Tulsi Ramayan")
    }

    // ---- soft-delete ----

    @Test
    fun removal_only_difference_is_a_conflict_flagged_removalOnly() {
        val local = listOf(book(id = 1, uid = "u1", title = "Godaan", removed = false))
        val incoming = listOf(book(id = 2, uid = "u1", title = "Godaan", removed = true))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.conflicts).hasSize(1)
        assertThat(plan.conflicts[0].isRemovalOnly).isTrue()
        // Never applied silently — it is surfaced, not in toAdd, not identical.
        assertThat(plan.identical).isEqualTo(0)
        assertThat(plan.toAdd).isEmpty()
    }

    @Test
    fun conflict_with_field_and_removal_difference_is_not_removalOnly() {
        val local = listOf(book(id = 1, uid = "u1", title = "Godaan", genre = "A", removed = false))
        val incoming = listOf(book(id = 2, uid = "u1", title = "Godaan", genre = "B", removed = true))

        val plan = LibraryMergeEngine.plan(local, incoming)

        assertThat(plan.conflicts).hasSize(1)
        assertThat(plan.conflicts[0].isRemovalOnly).isFalse()
    }

    // ---- robustness ----

    @Test
    fun two_incoming_books_do_not_fan_in_onto_one_local_row() {
        // Both incoming share local's ISBN (a malformed/cross-wired file).
        // Only the first may claim the local row; the second is treated as new.
        val local = listOf(book(id = 1, uid = "uA", title = "Sapiens", isbn = "9780001"))
        val incoming = listOf(
            book(id = 2, uid = "uB", title = "Sapiens", isbn = "9780001"),
            book(id = 3, uid = "uC", title = "Sapiens (copy)", isbn = "9780001"),
        )

        val plan = LibraryMergeEngine.plan(local, incoming)

        // First matched (identical), second could not claim the same row → added.
        assertThat(plan.identical).isEqualTo(1)
        assertThat(plan.toAdd).hasSize(1)
        assertThat(plan.toAdd[0].title).isEqualTo("Sapiens (copy)")
    }

    @Test
    fun merging_the_same_export_again_is_a_noop() {
        val lib = listOf(
            book(id = 1, uid = "u1", title = "Godaan", isbn = "111"),
            book(id = 2, uid = "u2", title = "Kabir", author = "Kabir"),
        )
        // Incoming == the same library (idempotency: re-importing a file you
        // already merged changes nothing).
        val plan = LibraryMergeEngine.plan(lib, lib)

        assertThat(plan.isNoOp).isTrue()
        assertThat(plan.identical).isEqualTo(2)
    }

    @Test
    fun empty_inputs_are_noop() {
        assertThat(LibraryMergeEngine.plan(emptyList(), emptyList()).isNoOp).isTrue()
    }

    @Test
    fun all_incoming_added_into_empty_local() {
        val incoming = listOf(
            book(id = 1, uid = "u1", title = "A", isbn = "111"),
            book(id = 2, uid = "u2", title = "B"),
        )
        val plan = LibraryMergeEngine.plan(emptyList(), incoming)
        assertThat(plan.toAdd).hasSize(2)
    }

    // ---- helpers ----

    @Test
    fun normIsbn_strips_spaces_hyphens_and_uppercases() {
        assertThat("978-0-00 1x".normIsbn()).isEqualTo("9780001X")
        assertThat((null as String?).normIsbn()).isEqualTo("")
    }

    @Test
    fun jaccard_basic() {
        assertThat(jaccard(setOf("a", "b"), setOf("a", "b"))).isEqualTo(1.0)
        assertThat(jaccard(setOf("a", "b"), setOf("c", "d"))).isEqualTo(0.0)
        assertThat(jaccard(setOf("a", "b"), setOf("a"))).isWithin(1e-9).of(0.5)
    }

    @Test
    fun tokenSet_is_script_agnostic_and_drops_punctuation() {
        val b = book(title = "Kabir, Ke Dohe!", author = "कबीर")
        val tokens = tokenSet(b)
        assertThat(tokens).contains("kabir")
        assertThat(tokens).contains("dohe")
        assertThat(tokens).contains("कबीर")
        // single-char / punctuation dropped
        assertThat(tokens).doesNotContain(",")
    }
}
