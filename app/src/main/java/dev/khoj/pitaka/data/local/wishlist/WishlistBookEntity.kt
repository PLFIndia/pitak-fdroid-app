package dev.khoj.pitaka.data.local.wishlist

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted form of [dev.khoj.pitaka.domain.model.WishlistBook].
 *
 * Schema mirrors pitaka.md §4.1.B amended by §1.2 D8 (titleTransliteration)
 * and §1.2 D11 (needs_metadata for skeleton scans).
 *
 * `source` is stored as the enum name (MANUAL / SCANNED). `priority` is an
 * Int per the spec; the UI presents three chips.
 */
@Entity(
    tableName = "wishlist_books",
    indices = [
        Index(value = ["isbn"], unique = true),
        Index(value = ["added_date"]),
        Index(value = ["priority"]),
        Index(value = ["purchased"]),
    ],
)
data class WishlistBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "title_transliteration")
    val titleTransliteration: String? = null,

    @ColumnInfo(name = "author")
    val author: String? = null,

    @ColumnInfo(name = "isbn")
    val isbn: String? = null,

    @ColumnInfo(name = "publisher")
    val publisher: String? = null,

    @ColumnInfo(name = "published_year")
    val publishedYear: Int? = null,

    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,

    @ColumnInfo(name = "price_estimate")
    val priceEstimate: Double? = null,

    @ColumnInfo(name = "priority", defaultValue = "1")
    val priority: Int = 1,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "added_date")
    val addedDate: Long,

    @ColumnInfo(name = "purchased", defaultValue = "0")
    val purchased: Boolean = false,

    @ColumnInfo(name = "purchased_date")
    val purchasedDate: Long? = null,

    @ColumnInfo(name = "needs_metadata", defaultValue = "0")
    val needsMetadata: Boolean = false,
)
