package dev.khoj.pitaka.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IsbnMetadataCacheDao {

    @Query("SELECT * FROM isbn_metadata_cache WHERE isbn = :isbn LIMIT 1")
    suspend fun get(isbn: String): IsbnMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IsbnMetadataCacheEntity)

    @Query("DELETE FROM isbn_metadata_cache WHERE isbn = :isbn")
    suspend fun delete(isbn: String)

    @Query("DELETE FROM isbn_metadata_cache WHERE fetched_at < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM isbn_metadata_cache")
    suspend fun count(): Int
}
