package dev.khoj.pitaka.data.backup

import com.squareup.moshi.Json

/**
 * On-disk manifest for a Pitaka backup archive (§1.1: versioned shape).
 *
 * `schemaVersion` is bumped only on breaking changes. Restore refuses
 * cleanly on `manifest.schemaVersion > KNOWN_SCHEMA_VERSION`.
 *
 * Boolean `hasX` fields are forward-compat: a future archive that omits
 * (say) wishlist.db should set `hasWishlist=false` so restore knows the
 * file is intentionally absent rather than corrupt.
 */
data class BackupManifest(
    @Json(name = "schemaVersion") val schemaVersion: Int = KNOWN_SCHEMA_VERSION,
    @Json(name = "exportedAt")    val exportedAt: Long,
    @Json(name = "hasBooks")      val hasBooks: Boolean = true,
    @Json(name = "hasWishlist")   val hasWishlist: Boolean = true,
    @Json(name = "hasBorrowers")  val hasBorrowers: Boolean = true,
    @Json(name = "hasBackupBlob") val hasBackupBlob: Boolean = true,
    @Json(name = "hasCovers")     val hasCovers: Boolean = false,
    @Json(name = "backupHint")    val backupHint: String? = null,
) {
    companion object {
        const val KNOWN_SCHEMA_VERSION: Int = 1
    }
}
