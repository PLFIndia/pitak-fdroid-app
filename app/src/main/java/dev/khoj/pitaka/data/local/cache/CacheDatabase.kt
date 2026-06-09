package dev.khoj.pitaka.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [IsbnMetadataCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun isbnMetadataCacheDao(): IsbnMetadataCacheDao

    companion object {
        const val NAME = "cache.db"
        const val ISBN_METADATA_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000 // 30 days (§14.7)
    }
}
