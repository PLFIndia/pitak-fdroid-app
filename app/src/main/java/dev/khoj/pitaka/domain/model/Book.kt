package dev.khoj.pitaka.domain.model

/**
 * Pure domain model for a book in the user's library.
 *
 * Schema reference: pitaka.md §4.1.A (as amended by §1.2 D4, D8, D30).
 * - `id`: 0 means "not yet persisted"; Room auto-generates on insert.
 * - `title`: required, stored in native script (UTF-8).
 * - `titleTransliteration`: optional Roman-script form for search (D8).
 * - No `status` field (D4 — lent-state lives only in the vault).
 * - `notes` is unencrypted on device but stripped from publish output at
 *   publish time, not at write time (D30).
 * - `addedDate` is epoch millis at insert; never edited (D30 read-only).
 * - `copyCount` defaults to 1; increments via the duplicate-ISBN dialog (D2).
 */
data class Book(
    val id: Long = 0L,
    /**
     * Stable, globally-unique identity for this book, opaque string (UUID).
     * Distinct from [id] (a per-device autoincrement that is meaningless across
     * installs). `bookUid` is minted once at first persist (see Book.toEntity)
     * and then travels through export/import unchanged, so the SAME logical book
     * carries the SAME uid on every device that received it via a shared file.
     *
     * This is the primary cross-device merge key (PLAN-merge.md): a book that
     * reached another maintainer's phone through an exported file reconciles by
     * uid; a book independently scanned on two phones reconciles by [isbn]; a
     * no-ISBN book typed independently has two different uids and is surfaced as
     * a possible-duplicate for the user to resolve.
     *
     * Nullable in the domain only to mean "not yet persisted / not yet minted";
     * every persisted row has a non-null uid (the mapper guarantees it).
     */
    val bookUid: String? = null,
    val title: String,
    val titleTransliteration: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val genre: String? = null,
    val coverUrl: String? = null,
    val pageCount: Int? = null,
    val language: String? = null,
    val notes: String? = null,
    /**
     * Physical location of this copy on the user's shelves (e.g. "Living room
     * shelf 3 · row 2", "Office cupboard top", "अलमारी 2"). Free-form Unicode
     * to match D8's bilingual posture. Searchable per D16.
     * Stripped from publish output (paired with D4's privacy posture) — the
     * world doesn't need to know where my Wittgenstein sits.
     */
    val location: String? = null,
    /**
     * How this copy was acquired (provenance), as a fixed category. Nullable —
     * an untouched book stays blank rather than falsely claiming "Purchased".
     * Paired with [sourceDetail] for the free-form specifics. Like [location]
     * and [notes], this is private provenance: STRIPPED from publish output
     * (redactForPublish), kept in on-device storage and local export only.
     */
    val sourceType: SourceType? = null,
    /**
     * Free-form, bilingual specifics of where this copy came from (e.g.
     * "Bahrisons Delhi", "उपहार from Ravi"). Nullable Unicode to match D8's
     * bilingual posture. Stripped from publish alongside [sourceType].
     */
    val sourceDetail: String? = null,
    /**
     * Reader age band this book targets. Nullable — blank when unset. Persisted
     * as the enum's stable [AgeGroup.token] string (e.g. "above-3"), NOT its
     * ordinal — the ordinal proved brittle (renaming/reordering silently shifts
     * the meaning of existing rows). Band order for sorting comes from
     * [AgeGroup.sortRank], not from the token's alphabetical order. Public
     * catalog info (useful for a kids' / community library) — NOT stripped at
     * publish; shown on GitHub Pages.
     */
    val ageGroup: AgeGroup? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val copyCount: Int = 1,
    val needsMetadata: Boolean = false,
    /**
     * Soft-delete flag (PLAN-merge.md). The app never hard-deletes a catalogued
     * book; "removing" it sets this true. A removed book:
     *  - stays VISIBLE in the Library/search with a "removed" badge (Q-REMOVED-VIS
     *    option B), but exposes NO mutating actions (no lend/edit) — view + restore
     *    only;
     *  - is STRIPPED from publish unconditionally (a public lending catalogue must
     *    not advertise books that left the collection);
     *  - merges like any other field: a removed-flag difference between two devices
     *    is SURFACED to the user, never applied silently.
     *
     * Pairs with [removedAt] (epoch ms when removed, null when active) for "removed
     * N days ago" affordances and as a tiebreak hint in merge surfacing.
     */
    val removed: Boolean = false,
    val removedAt: Long? = null,
    /**
     * Maintainer attribution (PLAN-merge.md D41): the handle of the maintainer
     * whose app first catalogued this book, stamped at creation from that app's
     * `maintainerName` pref. Travels through export/import and survives merges, so
     * a community library can show "added by Asha" / filter by contributor.
     *
     * Self-asserted, NOT cryptographically signed — attribution for coordination
     * among trusted maintainers, not proof of authorship. Null/blank when the app
     * has no maintainer name set.
     */
    val addedBy: String? = null,
) {
    /**
     * Fixed provenance categories for [sourceType]. Stored as [name] in the DB
     * (nullable). Nested to mirror [dev.khoj.pitaka.domain.model.WishlistBook.Source].
     * OTHER is the escape hatch; the free-form [sourceDetail] carries the rest.
     */
    enum class SourceType { PURCHASED, GIFT, DONATED, INHERITED, OTHER }

    /**
     * Reader age band.
     *
     * Persisted as [token] (a stable lowercase string of letters/digits/'-'),
     * NOT the ordinal — so renaming or reordering members can never silently
     * change what an existing row means. [sortRank] (not declaration order, not
     * the token's alphabetical order) defines band order for the Age-group sort.
     *
     * [fromToken] is deliberately tolerant: it accepts the current [token], the
     * current enum [name], AND the LEGACY enum names from the pre-"above N"
     * scheme (AGE_0_5 / AGE_6_10 / AGE_11_16 / ADVANCE) so that old JSON backups
     * and exported files still import. The legacy→new mapping mirrors the v9→v10
     * DB migration exactly (11–16 → above-10; nothing maps to above-15).
     * Anything unrecognised returns null (treated as "unset"), never throws.
     */
    enum class AgeGroup(val token: String, val sortRank: Int) {
        ABOVE_3("above-3", 0),
        ABOVE_6("above-6", 1),
        ABOVE_10("above-10", 2),
        ABOVE_15("above-15", 3),
        ADVANCED("advanced", 4);

        companion object {
            fun fromToken(raw: String?): AgeGroup? {
                val key = raw?.trim()?.lowercase() ?: return null
                if (key.isEmpty()) return null
                entries.firstOrNull { it.token == key }?.let { return it }
                // Current enum name (e.g. "ABOVE_3") and legacy names.
                return when (key) {
                    "above_3" -> ABOVE_3
                    "above_6" -> ABOVE_6
                    "above_10" -> ABOVE_10
                    "above_15" -> ABOVE_15
                    "advanced" -> ADVANCED
                    // Legacy pre-"above N" scheme (mirrors MIGRATION_9_10).
                    "age_0_5" -> ABOVE_3
                    "age_6_10" -> ABOVE_6
                    "age_11_16" -> ABOVE_10
                    "advance" -> ADVANCED
                    else -> null
                }
            }
        }
    }

    companion object {
        const val EMPTY_ID = 0L
    }
}
