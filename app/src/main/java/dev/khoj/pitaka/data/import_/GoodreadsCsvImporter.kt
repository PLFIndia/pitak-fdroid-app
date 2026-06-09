package dev.khoj.pitaka.data.import_

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.WishlistBook
import javax.inject.Inject

/**
 * Parses a Goodreads "Export library" CSV.
 *
 * Verified columns (2026-05): Book Id, Title, Author, Author l-f,
 * Additional Authors, ISBN, ISBN13, My Rating, Average Rating, Publisher,
 * Binding, Number of Pages, Year Published, Original Publication Year,
 * Date Read, Date Added, Bookshelves, Bookshelves with positions,
 * Exclusive Shelf, My Review, Spoiler, Private Notes, Read Count, Owned Copies.
 *
 * Mapping decisions (Phase 3):
 *  - `Exclusive Shelf == "to-read"` → Wishlist; everything else → Library.
 *    Spec D24 + the planning note in PLAN.md: this is the right default for
 *    the typical Goodreads user. Mis-categorized rows are two taps from
 *    correct after import.
 *  - ISBN columns from Goodreads come wrapped as `="..."` to force Excel to
 *    keep leading zeros. We strip that wrapper.
 *  - `ISBN13` is preferred over `ISBN`; if both are empty the field is null.
 *  - Per-row parse failures are collected, not raised — the user gets a
 *    summary at the end (D26: no terminal dead-ends).
 */
class GoodreadsCsvImporter @Inject constructor() : Importer {

    override fun parse(text: String): ImportPayload {
        val rows = parseCsv(text)
        if (rows.isEmpty()) {
            return ImportPayload(emptyList(), emptyList(), listOf("CSV file is empty."))
        }
        val header = rows.first().map { it.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        if (!header.containsAll(REQUIRED_HEADERS)) {
            val missing = REQUIRED_HEADERS.filterNot { it in header }
            return ImportPayload(
                emptyList(),
                emptyList(),
                listOf("Not a Goodreads CSV — missing columns: ${missing.joinToString(", ")}"),
            )
        }

        val books = mutableListOf<Book>()
        val wishlist = mutableListOf<WishlistBook>()
        val errors = mutableListOf<String>()

        rows.drop(1).forEachIndexed { i, row ->
            val rowNum = i + 2 // 1-indexed + header
            try {
                val title = row.get(index, "Title")?.takeIf { it.isNotBlank() }
                    ?: run { errors.add("Row $rowNum: missing title."); return@forEachIndexed }
                val author = row.get(index, "Author")?.takeIf { it.isNotBlank() }
                val isbn13 = row.get(index, "ISBN13")?.unwrapGoodreadsIsbn()?.takeIf { it.isNotBlank() }
                val isbn10 = row.get(index, "ISBN")?.unwrapGoodreadsIsbn()?.takeIf { it.isNotBlank() }
                val isbn = isbn13 ?: isbn10
                val publisher = row.get(index, "Publisher")?.takeIf { it.isNotBlank() }
                val year = row.get(index, "Year Published")?.toIntOrNull()
                    ?: row.get(index, "Original Publication Year")?.toIntOrNull()
                val pages = row.get(index, "Number of Pages")?.toIntOrNull()
                val notes = row.get(index, "My Review")?.takeIf { it.isNotBlank() }
                val shelf = row.get(index, "Exclusive Shelf")?.lowercase()?.trim()

                if (shelf == "to-read") {
                    wishlist.add(
                        WishlistBook(
                            title = title,
                            author = author,
                            isbn = isbn,
                            publisher = publisher,
                            publishedYear = year,
                            notes = notes,
                            priority = WishlistBook.PRIORITY_MED,
                            source = WishlistBook.Source.MANUAL,
                        )
                    )
                } else {
                    books.add(
                        Book(
                            title = title,
                            author = author,
                            isbn = isbn,
                            publisher = publisher,
                            publishedYear = year,
                            pageCount = pages,
                            notes = notes,
                        )
                    )
                }
            } catch (t: Throwable) {
                errors.add("Row $rowNum: ${t.message ?: "parse error"}")
            }
        }

        return ImportPayload(books = books, wishlist = wishlist, parseErrors = errors)
    }

    companion object {
        private val REQUIRED_HEADERS = listOf("Title", "Exclusive Shelf")
    }
}

private fun List<String>.get(index: Map<String, Int>, name: String): String? =
    index[name]?.let { i -> if (i < size) this[i] else null }

private fun String.unwrapGoodreadsIsbn(): String =
    trim()
        .removePrefix("=")
        .trim('"')
        .replace("-", "")
        .replace(" ", "")
