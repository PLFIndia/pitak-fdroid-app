package dev.khoj.pitaka.data.export

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import dev.khoj.pitaka.domain.model.Book

/**
 * Moshi adapter for [Book.AgeGroup] used on the JSON export/import round-trip
 * ([PitakaExport]). Writes the stable [Book.AgeGroup.token] string and reads it
 * back via [Book.AgeGroup.fromToken], which is tolerant of:
 *  - the current token ("above-3" …),
 *  - the current enum name ("ABOVE_3" …),
 *  - and the LEGACY pre-"above N" enum names (AGE_0_5 / AGE_6_10 / AGE_11_16 /
 *    ADVANCE) that older app versions wrote into backups.
 *
 * Tolerance matters: [PitakaExport.books] is a `List<Book>`, and the importer
 * (PitakaJsonImporter) rejects the WHOLE file if any value fails to parse. The
 * default reflective enum adapter would throw on a legacy name; this adapter
 * maps it instead, so a backup taken before this change still imports. An
 * unrecognised value decodes to null ("unset") rather than failing the file.
 *
 * Registered in di/NetworkModule.provideMoshi BEFORE KotlinJsonAdapterFactory so
 * it wins for the AgeGroup type.
 */
class AgeGroupJsonAdapter {
    @ToJson
    fun toJson(value: Book.AgeGroup?): String? = value?.token

    @FromJson
    fun fromJson(value: String?): Book.AgeGroup? = Book.AgeGroup.fromToken(value)
}
