package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookRepository
import javax.inject.Inject

/**
 * Update an existing book.
 *
 * Editability rules (D30, as amended):
 * - Read-only: `id` (the use case rejects updates that try to change it).
 * - `addedDate` ("Date added") is USER-EDITABLE: it defaults to today on
 *   creation but the form exposes a date picker, so the user can back-date a
 *   book they actually acquired earlier. (This supersedes the original D30
 *   "addedDate never edited" rule — the field is a user-meaningful acquisition
 *   date, and editing it deliberately re-orders the Recently-added sort.)
 * - Editable-with-warning: `isbn`, `coverUrl` (the use case allows the change;
 *   side effects — metadata-cache invalidation, cover re-download — are wired
 *   in Phase 2 when those subsystems exist).
 * - Freely editable: everything else.
 *
 * The "are you sure?" warning UI for ISBN edits is the ViewModel's job.
 */
class UpdateBookUseCase @Inject constructor(
    private val repository: BookRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data object TitleRequired : Result
        data object IdImmutable : Result
    }

    suspend operator fun invoke(updated: Book): Result {
        if (updated.title.isBlank()) return Result.TitleRequired
        val existing = repository.getById(updated.id) ?: return Result.NotFound
        if (existing.id != updated.id) return Result.IdImmutable
        repository.upsert(updated)
        return Result.Success
    }
}
