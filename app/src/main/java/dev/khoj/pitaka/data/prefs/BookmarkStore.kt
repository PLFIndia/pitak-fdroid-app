package dev.khoj.pitaka.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's bookmarks (links to other published library pages) as a
 * JSON array in its own DataStore file. No Room table — bookmarks are a small,
 * flat, ordered list with no relational queries, so DataStore is the right
 * weight (single source of truth, mirrors how app-level prefs are stored).
 *
 * Storage is append-ordered: the list is kept in insertion order; add appends,
 * update replaces by index, delete removes by index. The view shows them in
 * stored order (no sort).
 */
@Singleton
class BookmarkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val Context.bookmarkDataStore by preferencesDataStore(name = "pitaka_bookmarks")
    private val bookmarksKey = stringPreferencesKey("bookmarks_json")

    private val adapter = moshi.adapter<List<Bookmark>>(
        Types.newParameterizedType(List::class.java, Bookmark::class.java),
    )

    /** Observe the saved bookmarks in stored (insertion) order. */
    fun bookmarks(): Flow<List<Bookmark>> =
        context.bookmarkDataStore.data.map { prefs ->
            val json = prefs[bookmarksKey] ?: return@map emptyList()
            runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()
        }

    /** Appends a bookmark to the end of the list. */
    suspend fun add(bookmark: Bookmark) {
        mutate { it + bookmark }
    }

    /** Replaces the bookmark at [index]; no-op if the index is out of range. */
    suspend fun update(index: Int, bookmark: Bookmark) {
        mutate { current ->
            if (index !in current.indices) current
            else current.toMutableList().also { it[index] = bookmark }
        }
    }

    /** Removes the bookmark at [index]; no-op if out of range. */
    suspend fun removeAt(index: Int) {
        mutate { current ->
            if (index !in current.indices) current
            else current.toMutableList().also { it.removeAt(index) }
        }
    }

    private suspend fun mutate(transform: (List<Bookmark>) -> List<Bookmark>) {
        context.bookmarkDataStore.edit { prefs ->
            val current = prefs[bookmarksKey]
                ?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
                .orEmpty()
            prefs[bookmarksKey] = adapter.toJson(transform(current))
        }
    }
}
