package dev.khoj.pitaka.data.import_

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook

/**
 * Result of parsing an import file. Pure data; the use case downstream
 * does the database writes and returns a summary count.
 */
data class ImportPayload(
    val books: List<Book>,
    val wishlist: List<WishlistBook>,
    val parseErrors: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = books.isEmpty() && wishlist.isEmpty()
}

interface Importer {
    /**
     * Parse [text] into an [ImportPayload]. Implementations must not throw
     * on malformed rows — collect per-row errors into [ImportPayload.parseErrors]
     * so the user sees a count of what could and couldn't be ingested.
     */
    fun parse(text: String): ImportPayload
}
