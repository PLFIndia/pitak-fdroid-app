package dev.khoj.pitaka.data.local.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached metadata payload for a single ISBN (pitaka.md §4.1.D).
 *
 * `payload` stores the JSON-serialized [dev.khoj.pitaka.domain.model.BookMetadata]
 * verbatim — we don't shape the cache columns to mirror the metadata model,
 * because that creates schema churn every time we want to track a new field.
 *
 * `fetched_at` is the epoch-millis of the upstream call; TTL checks (§14.7,
 * 30 days) compare against `System.currentTimeMillis() - TTL`.
 *
 * `source` records which provider answered so we can later surface (or skip)
 * cached entries from a specific provider when refreshing.
 */
@Entity(tableName = "isbn_metadata_cache")
data class IsbnMetadataCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "isbn") val isbn: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
