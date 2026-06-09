package dev.khoj.pitaka.domain.usecase

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dev.khoj.pitaka.data.export.PitakaExport
import dev.khoj.pitaka.data.import_.ImportFormat
import dev.khoj.pitaka.data.import_.ImportFormatSniffer
import dev.khoj.pitaka.data.import_.PitakaJsonImporter
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.merge.LibraryMergeEngine
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Multi-maintainer library merge (PLAN-merge.md).
 *
 * Unlike [ImportLibraryUseCase] (a one-shot "load a file into my library"),
 * this reconciles two catalogues maintained on different devices and converges
 * them, surfacing anything ambiguous for the user instead of guessing.
 *
 * Two-stage flow:
 *
 *  STAGE 1 — library-ID gate (D40). Parse the file's `libraryId`. If it MATCHES
 *  this app's library ID, proceed straight to the engine merge ([Outcome.Merged]).
 *  If it DIFFERS (or either side is blank / "unknown library"), do NOT merge
 *  silently — return [Outcome.DiffersDecision] carrying the parsed payload + both
 *  library names, so the UI can ask the user to JOIN or OVERWRITE. This is the
 *  namespace guard that stops a personal shelf and a community library from
 *  cross-polluting.
 *
 *  STAGE 2 — apply. For a match, the engine has already auto-applied the add-only
 *  union and surfaced conflicts/possible-duplicates. For a differ-decision the
 *  caller invokes [applyJoin] (non-destructive union + adopt the incoming ID) or
 *  [applyOverwrite] (replace local catalogue + adopt the ID — destructive, the
 *  guarded secondary).
 */
class MergeLibraryUseCase @Inject constructor(
    private val sniffer: ImportFormatSniffer,
    private val pitakaJson: PitakaJsonImporter,
    private val bookRepo: BookRepository,
    private val prefs: AppPreferences,
    private val moshi: Moshi,
) {
    /** Result of an engine merge (library IDs matched, or applied via Join). */
    data class MergeResult(
        /** Books added automatically (add-only union). */
        val added: Int,
        /** Matched + byte-equal; no action taken. */
        val identical: Int,
        /** Matched but differing — await user resolution. */
        val conflicts: List<LibraryMergeEngine.Conflict>,
        /** No-ISBN fuzzy near-misses — await user confirmation. */
        val possibleDuplicates: List<LibraryMergeEngine.PossibleDuplicate>,
    ) {
        val hasReviewItems: Boolean get() = conflicts.isNotEmpty() || possibleDuplicates.isNotEmpty()
    }

    /** Top-level outcome of [invoke]. */
    sealed interface Outcome {
        /** Library IDs matched (or both empty-and-equal): engine merge already applied. */
        data class Merged(val result: MergeResult) : Outcome

        /**
         * Library IDs DIFFER (D40). Nothing applied yet — the user must choose
         * JOIN or OVERWRITE. Carries the data needed to apply either, plus the
         * names for a legible warning.
         */
        data class DiffersDecision(
            val incomingBooks: List<Book>,
            val incomingLibraryId: String,
            val incomingLibraryName: String,
            val localLibraryName: String,
            /** True when the local library has no books — overwrite is then safe. */
            val localIsEmpty: Boolean,
        ) : Outcome

        /** Could not even parse / wrong format. */
        data class Failed(val message: String) : Outcome
    }

    /** How the user chose to resolve one surfaced conflict / possible-duplicate. */
    enum class Resolution { KEEP_MINE, TAKE_THEIRS, KEEP_BOTH }

    @OptIn(ExperimentalStdlibApi::class)
    suspend operator fun invoke(text: String): Outcome {
        if (sniffer.detect(text) != ImportFormat.PitakaJson) {
            return Outcome.Failed(
                "Merge needs a Pitak library file (.json exported from Pitak). " +
                    "Other formats can be brought in with Import instead."
            )
        }

        // Read the library namespace fields off the raw export (the importer
        // normalises books but drops the envelope fields). The incoming ID is
        // validated against LibraryId: a malformed/corrupt ID (oversized,
        // non-hex, control chars) is treated as ABSENT, so the file falls into
        // the DiffersDecision path — never silently merged, never adopted, never
        // displayed as junk. Honors the same string limitation as QR pairing.
        val rawExport = runCatching { moshi.adapter<PitakaExport>().fromJson(text) }.getOrNull()
        val incomingLibraryId = dev.khoj.pitaka.domain.model.LibraryId
            .normalizeOrNull(rawExport?.libraryId.orEmpty()).orEmpty()
        val incomingLibraryName = rawExport?.libraryName.orEmpty().trim()

        val payload = pitakaJson.parse(text)
        if (payload.books.isEmpty() && payload.parseErrors.isNotEmpty()) {
            return Outcome.Failed(payload.parseErrors.first())
        }

        val localLibraryId = prefs.getOrCreateLibraryId().trim()

        // ID gate (D40). Match → merge. Differ (or incoming has no ID) → decision.
        val idsMatch = incomingLibraryId.isNotEmpty() && incomingLibraryId == localLibraryId
        if (!idsMatch) {
            val local = bookRepo.getAll()
            return Outcome.DiffersDecision(
                incomingBooks = payload.books,
                incomingLibraryId = incomingLibraryId,
                incomingLibraryName = incomingLibraryName,
                localLibraryName = prefs.libraryName().first(),
                localIsEmpty = local.isEmpty(),
            )
        }

        return Outcome.Merged(applyEngineMerge(payload.books))
    }

    /**
     * JOIN (D40, the non-destructive default for a differ-IDs file): union the
     * incoming books into the local library via the engine, AND adopt the
     * incoming library ID + name so the two devices share a namespace going
     * forward. Nobody loses data (D39-consistent).
     */
    suspend fun applyJoin(decision: Outcome.DiffersDecision): MergeResult {
        if (decision.incomingLibraryId.isNotEmpty()) {
            prefs.setLibraryId(decision.incomingLibraryId)
            if (decision.incomingLibraryName.isNotEmpty()) {
                prefs.setLibraryName(decision.incomingLibraryName)
            }
        }
        return applyEngineMerge(decision.incomingBooks)
    }

    /**
     * OVERWRITE (D40, the guarded secondary): replace the local catalogue with the
     * incoming one and adopt its library ID + name. Destructive — intended for a
     * fresh/empty install becoming a clean replica. The caller is responsible for
     * an explicit confirm before invoking this.
     */
    suspend fun applyOverwrite(decision: Outcome.DiffersDecision) {
        // Replace: drop existing rows, then insert the incoming set fresh.
        for (b in bookRepo.getAll()) bookRepo.delete(b.id)
        for (book in decision.incomingBooks) {
            bookRepo.upsert(book.copy(id = Book.EMPTY_ID))
        }
        if (decision.incomingLibraryId.isNotEmpty()) {
            prefs.setLibraryId(decision.incomingLibraryId)
            if (decision.incomingLibraryName.isNotEmpty()) {
                prefs.setLibraryName(decision.incomingLibraryName)
            }
        }
    }

    /** Run the engine against the current library and auto-apply the add-only union. */
    private suspend fun applyEngineMerge(incoming: List<Book>): MergeResult {
        val local = bookRepo.getAll()
        val plan = LibraryMergeEngine.plan(local = local, incoming = incoming)
        for (book in plan.toAdd) {
            // Reset id (fresh local row); KEEP bookUid so future merges reconcile.
            bookRepo.upsert(book.copy(id = Book.EMPTY_ID))
        }
        return MergeResult(
            added = plan.toAdd.size,
            identical = plan.identical,
            conflicts = plan.conflicts,
            possibleDuplicates = plan.possibleDuplicates,
        )
    }

    /**
     * Apply the user's choice for one surfaced conflict / possible-duplicate.
     *  - KEEP_MINE  → no-op.
     *  - TAKE_THEIRS→ overwrite the local row in place (preserve local id +
     *    bookUid so identity is stable; take the incoming catalogue fields).
     *  - KEEP_BOTH  → insert the incoming as a NEW separate book.
     *
     * KEEP_BOTH identity hygiene (F-21): the conflict was surfaced because the
     * incoming book matched the local one by `bookUid` OR `isbn` — both of
     * which are UNIQUE columns on `books` (BookEntity). Re-inserting the
     * incoming book verbatim would carry that same `bookUid` (and, for an
     * ISBN match, the same `isbn`) into a second row, collide with the unique
     * index, and — because the DAO uses `@Upsert`, which falls back to
     * update-by-primary-key on conflict and the new row's id is 0 — silently
     * vanish instead of duplicating. So a true "keep both" mints a fresh
     * identity for the copy: a new `bookUid` (null → the mapper assigns one)
     * and a dropped `isbn`. The original row keeps the ISBN; the duplicate is
     * a deliberate separate catalogue entry with no ISBN, consistent with the
     * D2 invariant that two rows never share one ISBN (duplicate scans bump
     * `copyCount` rather than making a second ISBN-bearing row).
     */
    suspend fun applyResolution(local: Book, incoming: Book, resolution: Resolution) {
        when (resolution) {
            Resolution.KEEP_MINE -> Unit
            Resolution.TAKE_THEIRS ->
                bookRepo.upsert(incoming.copy(id = local.id, bookUid = local.bookUid))
            Resolution.KEEP_BOTH ->
                bookRepo.upsert(
                    incoming.copy(
                        id = Book.EMPTY_ID,
                        // Fresh cross-device identity for the duplicate so it can
                        // never collide with the original's UNIQUE book_uid.
                        bookUid = null,
                        // Drop the ISBN: it is UNIQUE and still held by the
                        // original row. The duplicate is an intentional separate
                        // entry (D2 — two rows never share an ISBN).
                        isbn = null,
                    )
                )
        }
    }
}
