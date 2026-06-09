package dev.khoj.pitaka.data.images

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.repository.BookRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot guard seam for the cover-heal migration. Production delegates to
 * [AppPreferences] (DataStore-backed); tests inject an in-memory fake so the
 * healer's logic can be exercised without standing up DataStore.
 */
interface CoverHealFlag {
    suspend fun isHealed(): Boolean
    suspend fun markHealed()
}

/** [AppPreferences]-backed [CoverHealFlag]. */
@Singleton
class PrefsCoverHealFlag @Inject constructor(
    private val prefs: AppPreferences,
) : CoverHealFlag {
    override suspend fun isHealed(): Boolean = prefs.coversHealed().first()
    override suspend fun markHealed() = prefs.setCoversHealed(true)
}

/**
 * One-shot cover-heal migration (PLAN-covers.md D4).
 *
 * Applies [CoverHealPlanner] to the live library exactly once, guarded by
 * [CoverHealFlag]. Rationalises legacy id-named / stale / cross-wired cover
 * references (the original "same cover on two books" bug) into the post-D1
 * UUID-relative scheme, salvaging covers that are uniquely owned and present,
 * and clearing the genuinely ambiguous ones.
 *
 * All risky decision logic lives in the pure [CoverHealPlanner]; this class
 * only performs IO (read rows, list files, rename/delete files, upsert rows)
 * and is intentionally thin. Idempotent: re-running on an already-healed
 * library produces an empty plan, so a missed flag write is self-correcting.
 */
@Singleton
class CoverHealer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val books: BookRepository,
    private val flag: CoverHealFlag,
) {

    /** Runs the heal once; no-op if already done. Returns true if it ran. */
    suspend fun runIfNeeded(): Boolean {
        if (flag.isHealed()) return false
        runCatching { heal() }
        // Mark done regardless of individual file hiccups: the plan is derived
        // from current state, so a partial apply still leaves the DB
        // consistent, and re-running would only repeat best-effort cleanup.
        flag.markHealed()
        return true
    }

    private suspend fun heal() {
        val coversDir = File(context.filesDir, CoverPaths.COVERS_DIR)
        val existingLeaves: Set<String> =
            coversDir.listFiles()?.filter { it.isFile }?.map { it.name }?.toSet() ?: emptySet()

        // One-shot snapshot — NOT observeAll().first(), whose initial Flow
        // emission can race startup writes and return an empty list (which
        // would make the planner treat every cover file as an orphan).
        val allBooks = books.getAll()
        val inputs = allBooks.map { CoverHealPlanner.Input(it.id, it.coverUrl) }

        val plan = CoverHealPlanner.plan(
            inputs = inputs,
            existingLeaves = existingLeaves,
            newUuid = { UUID.randomUUID().toString() },
        )
        if (plan.isEmpty) return

        // 1. File renames first (salvage), so a row rewrite never points at a
        //    file that hasn't moved yet.
        for (r in plan.fileRenames) {
            val from = File(coversDir, r.fromLeaf)
            val to = File(coversDir, r.toLeaf)
            runCatching { if (from.exists()) from.renameTo(to) }
        }

        // 2. Row mutations.
        val byId = allBooks.associateBy { it.id }
        for (action in plan.rowActions) {
            when (action) {
                is CoverHealPlanner.RowAction.Clear -> {
                    byId[action.id]?.let { books.upsert(it.copy(coverUrl = null)) }
                }
                is CoverHealPlanner.RowAction.Rewrite -> {
                    byId[action.id]?.let { books.upsert(it.copy(coverUrl = action.newCoverUrl)) }
                }
            }
        }

        // 3. Delete orphaned files last (after rows that referenced them are
        //    cleared), reclaiming space without risking a live reference.
        for (leaf in plan.deleteLeaves) {
            runCatching { File(coversDir, leaf).delete() }
        }
    }
}
