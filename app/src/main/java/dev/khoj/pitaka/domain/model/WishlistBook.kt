package dev.khoj.pitaka.domain.model

/**
 * A book the user wants to buy (pitaka.md §4.1.B as amended by §1.2 D8).
 *
 * Wishlist is fully separate from Library — its own Room DB, its own UI,
 * its own export bucket. The two are tied together only by the user's
 * intent ("when I buy this, move it to Library") and the D2 duplicate-ISBN
 * check (a wishlist add looks across both tables).
 *
 * No vault data. D4: anything published or backed up to the cloud is
 * library + wishlist; the unencrypted DBs cannot leak loan information
 * because they don't contain any.
 */
data class WishlistBook(
    val id: Long = 0L,
    val title: String,
    val titleTransliteration: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val coverUrl: String? = null,
    val priceEstimate: Double? = null,
    /** 0 = low, 1 = med (default), 2 = high. */
    val priority: Int = 1,
    val notes: String? = null,
    val source: Source = Source.MANUAL,
    val addedDate: Long = System.currentTimeMillis(),
    val purchased: Boolean = false,
    val purchasedDate: Long? = null,
    val needsMetadata: Boolean = false,
) {
    enum class Source { MANUAL, SCANNED }

    companion object {
        const val PRIORITY_LOW = 0
        const val PRIORITY_MED = 1
        const val PRIORITY_HIGH = 2
    }
}
