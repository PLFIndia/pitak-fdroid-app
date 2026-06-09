package dev.khoj.pitaka.domain.merge

import dev.khoj.pitaka.domain.model.Book

/**
 * Pure, side-effect-free engine for the multi-maintainer library merge
 * (PLAN-merge.md). Given the LOCAL library and an INCOMING library (parsed from
 * another maintainer's exported file), it produces a [MergePlan] the caller
 * applies. No Android, no I/O, no repository — exhaustively unit-testable.
 *
 * Semantics (locked decisions, PLAN-merge.md):
 *  - **add-only + manual conflict surfacing.** Union the catalogues; auto-add
 *    incoming books the local device doesn't have; NEVER silently overwrite an
 *    existing book. When the same book differs, surface it for the user.
 *  - **identity, evaluated in order:**
 *      1. `bookUid` — the stable cross-device id. Same uid ⇒ same book. (A book
 *         that reached this device via a prior file carries its origin uid.)
 *      2. `isbn` — for books with no uid match. Same ISBN ⇒ same physical book
 *         (independently scanned on two phones reconciles here).
 *      3. no uid, no isbn ⇒ fall to fuzzy.
 *  - **no-ISBN fuzzy (Q-NOISBN = B):** a no-ISBN incoming book with no exact
 *    match is compared by normalised title+author to local no-ISBN books. A
 *    close match is surfaced as a POSSIBLE DUPLICATE for the user to confirm —
 *    never auto-merged (that would be guessing), never silently added either.
 *  - **soft-delete is just a field.** A `removed`-flag difference between two
 *    matched books is surfaced like any other conflict (it is never applied
 *    silently in either direction).
 *
 * What is automatic vs surfaced:
 *  - [MergePlan.toAdd]     — incoming books with NO local match. Auto-applied.
 *  - [MergePlan.identical] — matched + field-equal. No-op (counted only).
 *  - [MergePlan.conflicts] — matched but differing. User resolves (row-level v1).
 *  - [MergePlan.possibleDuplicates] — no-ISBN fuzzy near-misses. User confirms.
 *
 * The engine matches each incoming book to AT MOST one local book, and never
 * matches two incoming books to the same local book (first-claim wins, so a
 * messy incoming file can't fan-in onto one local row).
 */
object LibraryMergeEngine {

    /** Default Jaccard-token similarity threshold for the no-ISBN fuzzy pass. */
    const val DEFAULT_FUZZY_THRESHOLD: Double = 0.6

    /** How an incoming book was matched to a local book (for UI explanation). */
    enum class MatchKind { UID, ISBN }

    /**
     * An incoming book that matched a local book by uid or ISBN but whose
     * publishable fields differ. Carries both sides so the UI can show a diff
     * and offer row-level resolution (keep local / take incoming / keep both).
     */
    data class Conflict(
        val local: Book,
        val incoming: Book,
        val matchedBy: MatchKind,
    ) {
        /** True when the only difference is the soft-delete state. */
        val isRemovalOnly: Boolean
            get() = local.copy(removed = incoming.removed, removedAt = incoming.removedAt)
                .mergeEquals(incoming)
    }

    /**
     * A no-ISBN incoming book that fuzzily resembles a local no-ISBN book but is
     * not an exact match. Surfaced for the user to either merge (same book) or
     * add separately. [similarity] is the token-set Jaccard score in (0,1].
     */
    data class PossibleDuplicate(
        val local: Book,
        val incoming: Book,
        val similarity: Double,
    )

    data class MergePlan(
        val toAdd: List<Book>,
        val conflicts: List<Conflict>,
        val possibleDuplicates: List<PossibleDuplicate>,
        val identical: Int,
    ) {
        val hasReviewItems: Boolean
            get() = conflicts.isNotEmpty() || possibleDuplicates.isNotEmpty()

        val isNoOp: Boolean
            get() = toAdd.isEmpty() && conflicts.isEmpty() && possibleDuplicates.isEmpty()
    }

    fun plan(
        local: List<Book>,
        incoming: List<Book>,
        fuzzyThreshold: Double = DEFAULT_FUZZY_THRESHOLD,
    ): MergePlan {
        // Indexes for O(1) exact matching. Blank keys are ignored.
        val localByUid = HashMap<String, Book>()
        val localByIsbn = HashMap<String, Book>()
        for (b in local) {
            b.bookUid?.takeIf { it.isNotBlank() }?.let { localByUid.putIfAbsent(it, b) }
            b.isbn?.normIsbn()?.takeIf { it.isNotBlank() }?.let { localByIsbn.putIfAbsent(it, b) }
        }

        // Local no-ISBN books are the fuzzy-match candidate pool.
        val localNoIsbn = local.filter { it.isbn.normIsbn().isBlank() }

        val toAdd = mutableListOf<Book>()
        val conflicts = mutableListOf<Conflict>()
        val possibleDuplicates = mutableListOf<PossibleDuplicate>()
        var identical = 0

        // A local row may be claimed by at most one incoming book (no fan-in).
        val claimedLocalIds = HashSet<Long>()

        for (inc in incoming) {
            val incUid = inc.bookUid?.takeIf { it.isNotBlank() }
            val incIsbn = inc.isbn.normIsbn().takeIf { it.isNotBlank() }

            // 1) uid match.
            val byUid = incUid?.let { localByUid[it] }?.takeIf { it.id !in claimedLocalIds }
            if (byUid != null) {
                claimedLocalIds += byUid.id
                if (byUid.mergeEquals(inc)) identical++
                else conflicts += Conflict(byUid, inc, MatchKind.UID)
                continue
            }

            // 2) ISBN match.
            val byIsbn = incIsbn?.let { localByIsbn[it] }?.takeIf { it.id !in claimedLocalIds }
            if (byIsbn != null) {
                claimedLocalIds += byIsbn.id
                if (byIsbn.mergeEquals(inc)) identical++
                else conflicts += Conflict(byIsbn, inc, MatchKind.ISBN)
                continue
            }

            // 3) No exact match. If the incoming book has an ISBN, it is genuinely
            //    new here — add it. If it has NO ISBN, try a fuzzy pass against
            //    local no-ISBN books before deciding.
            if (incIsbn != null) {
                toAdd += inc
                continue
            }

            val candidate = bestFuzzyMatch(inc, localNoIsbn, claimedLocalIds, fuzzyThreshold)
            if (candidate != null) {
                claimedLocalIds += candidate.first.id
                possibleDuplicates += PossibleDuplicate(candidate.first, inc, candidate.second)
            } else {
                toAdd += inc
            }
        }

        return MergePlan(
            toAdd = toAdd,
            conflicts = conflicts,
            possibleDuplicates = possibleDuplicates,
            identical = identical,
        )
    }

    /** Best unclaimed local no-ISBN book whose similarity ≥ threshold, or null. */
    private fun bestFuzzyMatch(
        incoming: Book,
        candidates: List<Book>,
        claimedLocalIds: Set<Long>,
        threshold: Double,
    ): Pair<Book, Double>? {
        val incTokens = tokenSet(incoming)
        if (incTokens.isEmpty()) return null
        var best: Book? = null
        var bestScore = 0.0
        for (c in candidates) {
            if (c.id in claimedLocalIds) continue
            val score = jaccard(incTokens, tokenSet(c))
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return if (best != null && bestScore >= threshold) best to bestScore else null
    }
}

/**
 * Field equality for merge purposes: do the two books describe the SAME
 * catalogue state? Compares the user-meaningful catalogue fields plus the
 * soft-delete flag. Deliberately IGNORES the per-device `id` and `addedDate`
 * (local bookkeeping, expected to differ across devices) and `bookUid` (already
 * established equal by the caller, or irrelevant for an ISBN match).
 */
internal fun Book.mergeEquals(other: Book): Boolean =
    title == other.title &&
        titleTransliteration == other.titleTransliteration &&
        author == other.author &&
        isbn.normIsbn() == other.isbn.normIsbn() &&
        publisher == other.publisher &&
        publishedYear == other.publishedYear &&
        genre == other.genre &&
        coverUrl == other.coverUrl &&
        pageCount == other.pageCount &&
        language == other.language &&
        notes == other.notes &&
        location == other.location &&
        sourceType == other.sourceType &&
        sourceDetail == other.sourceDetail &&
        ageGroup == other.ageGroup &&
        copyCount == other.copyCount &&
        needsMetadata == other.needsMetadata &&
        removed == other.removed

/** Normalise an ISBN for comparison: strip spaces/hyphens, uppercase (X check digit). */
internal fun String?.normIsbn(): String =
    this?.replace(Regex("[\\s-]"), "")?.uppercase().orEmpty()

/** Normalised title+author token set for fuzzy matching (lowercased, depunctuated). */
internal fun tokenSet(book: Book): Set<String> {
    val raw = buildString {
        append(book.title)
        book.author?.let { append(' ').append(it) }
        book.titleTransliteration?.let { append(' ').append(it) }
    }
    return raw
        .lowercase()
        // Keep letters (any script), combining marks, and digits; turn everything
        // else into a separator. \p{M} is essential for Indic scripts — Devanagari
        // / Gurmukhi vowel signs (e.g. the ी in कबीर) are Marks, not Letters, and
        // dropping them would shatter a word into fragments (D8 bilingual posture).
        .replace(Regex("[^\\p{L}\\p{M}\\p{Nd}\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 2 }
        .toSet()
}

/** Jaccard similarity of two token sets: |A∩B| / |A∪B|. 0 when both empty. */
internal fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() && b.isEmpty()) return 0.0
    val inter = a.count { it in b }
    val union = a.size + b.size - inter
    return if (union == 0) 0.0 else inter.toDouble() / union.toDouble()
}
