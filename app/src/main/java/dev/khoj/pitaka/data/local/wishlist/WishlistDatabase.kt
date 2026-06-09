package dev.khoj.pitaka.data.local.wishlist

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Wishlist database (pitaka.md §4.1.B — unencrypted).
 *
 * Tables: `wishlist_books`. Kept in its own database so:
 *  - export boundaries are clean (Library export vs Wishlist export).
 *  - migrations evolve independently (e.g. Library got `needs_metadata`
 *    and `location` separately).
 *  - the "publish my library" pipeline can never accidentally include
 *    wishlist data without an explicit decision.
 *
 * Versions:
 *   v1 (Phase 3) — initial schema.
 */
@Database(
    entities = [WishlistBookEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WishlistDatabase : RoomDatabase() {
    abstract fun wishlistBookDao(): WishlistBookDao

    companion object {
        const val NAME = "wishlist.db"
    }
}
