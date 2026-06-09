package dev.khoj.pitaka.data.import_

/**
 * Minimal RFC 4180 CSV parser. Returns rows of fields.
 *
 * Handles:
 *  - quoted fields with embedded commas, quotes (`""`), and newlines.
 *  - bare unquoted fields.
 *  - both \r\n and \n line endings.
 *  - trailing newline at EOF (no spurious empty final row).
 *
 * No external dep — the spec for the format we consume is small and we
 * don't want to take a library dependency for one parser.
 */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val current = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    val n = text.length

    while (i < n) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < n && text[i + 1] == '"' -> {
                    field.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = false
                    i++
                }
                else -> {
                    field.append(c)
                    i++
                }
            }
        } else {
            when (c) {
                '"' -> {
                    inQuotes = true
                    i++
                }
                ',' -> {
                    current.add(field.toString())
                    field.setLength(0)
                    i++
                }
                '\r' -> {
                    // ignore; the \n that follows will terminate the row
                    i++
                }
                '\n' -> {
                    current.add(field.toString())
                    field.setLength(0)
                    rows.add(current.toList())
                    current.clear()
                    i++
                }
                else -> {
                    field.append(c)
                    i++
                }
            }
        }
    }

    // Final field / row (no trailing newline case)
    if (field.isNotEmpty() || current.isNotEmpty()) {
        current.add(field.toString())
        rows.add(current.toList())
    }

    return rows
}
