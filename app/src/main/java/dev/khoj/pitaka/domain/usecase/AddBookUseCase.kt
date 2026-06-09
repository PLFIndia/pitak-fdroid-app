package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Add a book to the library.
 *
 * Validates that the title is non-blank (D26 — no terminal dead-ends; UI also
 * validates, but the use case enforces the invariant). Returns the new row id
 * on success, or a sealed [Result] describing the failure.
 *
 * Attribution (D41): when a NEW book (id == 0) has no `addedBy` yet, it is
 * stamped with this app's maintainer name (blank → null). This is the single
 * book-creation chokepoint, so every manually-added / scanned book gets
 * attributed without touching each caller. Edits (id != 0) and already-stamped
 * books are left untouched.
 *
 * Duplicate-ISBN handling (D2) is the caller's responsibility — the ViewModel
 * is expected to call [BookRepository.findByIsbn] first when an ISBN is
 * present, and route to the duplicate dialog before invoking this use case.
 */
class AddBookUseCase @Inject constructor(
    private val repository: BookRepository,
    private val prefs: AppPreferences,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data object TitleRequired : Result
    }

    suspend operator fun invoke(book: Book): Result {
        if (book.title.isBlank()) return Result.TitleRequired
        val stamped = if (book.id == Book.EMPTY_ID && book.addedBy.isNullOrBlank()) {
            book.copy(addedBy = prefs.maintainerName().first().ifBlank { null })
        } else {
            book
        }
        val id = repository.upsert(stamped)
        return Result.Success(id)
    }
}
