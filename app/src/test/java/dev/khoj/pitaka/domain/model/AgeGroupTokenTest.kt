package dev.khoj.pitaka.domain.model

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.khoj.pitaka.data.export.AgeGroupJsonAdapter
import dev.khoj.pitaka.data.export.PitakaExport
import org.junit.Test

/**
 * Guards the age-band token migration's backward-compatibility contract:
 *  - [Book.AgeGroup.fromToken] accepts new tokens, current names, AND the legacy
 *    pre-"above N" enum names (so old DB rows / backups don't break).
 *  - The Moshi [AgeGroupJsonAdapter] writes the token and reads legacy names,
 *    so a JSON backup produced before this change still imports as a whole.
 */
class AgeGroupTokenTest {

    @OptIn(ExperimentalStdlibApi::class)
    private val moshi = Moshi.Builder()
        .add(AgeGroupJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun fromToken_accepts_current_tokens() {
        assertThat(Book.AgeGroup.fromToken("above-3")).isEqualTo(Book.AgeGroup.ABOVE_3)
        assertThat(Book.AgeGroup.fromToken("above-6")).isEqualTo(Book.AgeGroup.ABOVE_6)
        assertThat(Book.AgeGroup.fromToken("above-10")).isEqualTo(Book.AgeGroup.ABOVE_10)
        assertThat(Book.AgeGroup.fromToken("above-15")).isEqualTo(Book.AgeGroup.ABOVE_15)
        assertThat(Book.AgeGroup.fromToken("advanced")).isEqualTo(Book.AgeGroup.ADVANCED)
    }

    @Test
    fun fromToken_accepts_current_enum_names_case_insensitively() {
        assertThat(Book.AgeGroup.fromToken("ABOVE_3")).isEqualTo(Book.AgeGroup.ABOVE_3)
        assertThat(Book.AgeGroup.fromToken("Advanced")).isEqualTo(Book.AgeGroup.ADVANCED)
    }

    @Test
    fun fromToken_maps_legacy_names_per_migration() {
        // Mirrors MIGRATION_9_10 / the B-decision mapping exactly.
        assertThat(Book.AgeGroup.fromToken("AGE_0_5")).isEqualTo(Book.AgeGroup.ABOVE_3)
        assertThat(Book.AgeGroup.fromToken("AGE_6_10")).isEqualTo(Book.AgeGroup.ABOVE_6)
        assertThat(Book.AgeGroup.fromToken("AGE_11_16")).isEqualTo(Book.AgeGroup.ABOVE_10)
        assertThat(Book.AgeGroup.fromToken("ADVANCE")).isEqualTo(Book.AgeGroup.ADVANCED)
    }

    @Test
    fun fromToken_unknown_or_blank_is_null() {
        assertThat(Book.AgeGroup.fromToken(null)).isNull()
        assertThat(Book.AgeGroup.fromToken("")).isNull()
        assertThat(Book.AgeGroup.fromToken("  ")).isNull()
        assertThat(Book.AgeGroup.fromToken("nonsense")).isNull()
        // Nothing legacy maps to above-15.
        assertThat(Book.AgeGroup.entries.none { Book.AgeGroup.fromToken("AGE_11_16") == Book.AgeGroup.ABOVE_15 }).isTrue()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun json_round_trips_via_token() {
        val export = PitakaExport(
            exportedAt = 1L,
            books = listOf(Book(title = "Kids Book", ageGroup = Book.AgeGroup.ABOVE_6)),
            wishlist = emptyList(),
        )
        val json = moshi.adapter<PitakaExport>().toJson(export)
        // Serialized as the stable token, not the enum name or ordinal.
        assertThat(json).contains("\"above-6\"")
        assertThat(json).doesNotContain("ABOVE_6")

        val back = moshi.adapter<PitakaExport>().fromJson(json)!!
        assertThat(back.books.single().ageGroup).isEqualTo(Book.AgeGroup.ABOVE_6)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun json_import_accepts_legacy_enum_name_backup() {
        // A backup produced by an OLD app version stored the enum NAME. The whole
        // file must still import (the importer rejects the entire file on one bad
        // enum), with the legacy band remapped to its new band.
        val legacy = """
            {"schemaVersion":3,"exportedAt":1,
             "books":[{"id":0,"title":"Old Tot","ageGroup":"AGE_0_5","addedDate":1,"copyCount":1,
                       "needsMetadata":false,"removed":false}],
             "wishlist":[],"libraryId":"","libraryName":""}
        """.trimIndent()
        val back = moshi.adapter<PitakaExport>().fromJson(legacy)!!
        assertThat(back.books.single().ageGroup).isEqualTo(Book.AgeGroup.ABOVE_3)
    }
}
