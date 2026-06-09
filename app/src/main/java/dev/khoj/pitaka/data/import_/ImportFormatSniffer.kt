package dev.khoj.pitaka.data.import_

import javax.inject.Inject

/**
 * Picks the right importer for a payload.
 *
 * Heuristic:
 *  - text starting with `{` and containing `"schemaVersion"` → Pitaka JSON.
 *  - text with a comma-separated first line that includes "Exclusive Shelf"
 *    or "Bookshelves" → Goodreads CSV.
 *  - otherwise → null and the use case reports "Unknown format."
 *
 * Deliberately conservative: we'd rather refuse cleanly than silently
 * misinterpret a file shape we don't understand.
 */
class ImportFormatSniffer @Inject constructor() {

    fun detect(text: String): ImportFormat? {
        val head = text.take(2_000).trim()
        return when {
            head.startsWith("{") && head.contains("\"schemaVersion\"") -> ImportFormat.PitakaJson
            looksLikeGoodreadsCsv(head)                                -> ImportFormat.GoodreadsCsv
            else                                                       -> null
        }
    }

    private fun looksLikeGoodreadsCsv(head: String): Boolean {
        val firstLine = head.lineSequence().firstOrNull() ?: return false
        return ',' in firstLine && ("Exclusive Shelf" in firstLine || "Bookshelves" in firstLine)
    }
}

enum class ImportFormat { PitakaJson, PitakaBundle, GoodreadsCsv }
