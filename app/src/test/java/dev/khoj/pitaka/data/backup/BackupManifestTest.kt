package dev.khoj.pitaka.data.backup

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class BackupManifestTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun roundtrips_through_moshi() {
        val a = moshi.adapter<BackupManifest>()
        val original = BackupManifest(
            schemaVersion = 1,
            exportedAt = 1_700_000_000_000L,
            hasBooks = true,
            hasWishlist = false,
            hasBorrowers = true,
            hasBackupBlob = true,
            backupHint = "the usual",
        )
        val json = a.toJson(original)
        val parsed = a.fromJson(json)
        assertThat(parsed).isEqualTo(original)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun absent_optional_fields_use_defaults_on_parse() {
        val a = moshi.adapter<BackupManifest>()
        val minimal = """{"schemaVersion":1,"exportedAt":1}"""
        val parsed = a.fromJson(minimal)!!
        assertThat(parsed.hasBooks).isTrue()
        assertThat(parsed.hasWishlist).isTrue()
        assertThat(parsed.hasBorrowers).isTrue()
        assertThat(parsed.hasBackupBlob).isTrue()
        assertThat(parsed.backupHint).isNull()
        // hasCovers defaults FALSE (not true like the other hasX flags): a
        // pre-D2 archive that predates cover bundling must parse as "no
        // covers present", not "covers present but missing".
        assertThat(parsed.hasCovers).isFalse()
    }
}
