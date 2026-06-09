package dev.khoj.pitaka.domain.repository

import dev.khoj.pitaka.domain.model.WishlistBook
import kotlinx.coroutines.flow.Flow

/**
 * Wishlist contract (pitaka.md §4.3).
 *
 * Phase 3 surface:
 *  - observe active / purchased / all (separate flows so the screen can
 *    filter without re-running queries on every key press).
 *  - search across title / transliteration / author / isbn (D16-equivalent
 *    for wishlist).
 *  - upsert, delete, find-by-isbn (used by the D2 duplicate-add dialog).
 *  - mark-purchased with optional move-to-library — this is the only
 *    write that crosses the wishlist↔library boundary, so it lives in
 *    a dedicated repository method instead of leaking the cross-DB
 *    operation into the use case.
 */
interface WishlistRepository {
    fun observeActive(sort: WishlistSort = WishlistSort.Priority): Flow<List<WishlistBook>>
    fun observePurchased(): Flow<List<WishlistBook>>
    fun search(query: String): Flow<List<WishlistBook>>

    suspend fun getById(id: Long): WishlistBook?
    suspend fun findByIsbn(isbn: String): WishlistBook?
    suspend fun upsert(book: WishlistBook): Long
    suspend fun delete(id: Long)
}

enum class WishlistSort {
    /** Default — D22-equivalent for wishlist. */
    Priority,
    RecentlyAdded,
}
