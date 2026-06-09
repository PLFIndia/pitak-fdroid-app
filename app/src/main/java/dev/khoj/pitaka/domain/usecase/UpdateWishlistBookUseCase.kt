package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Inject

/**
 * D30 mirror for wishlist: id and addedDate are read-only; everything else
 * is freely editable. The use case enforces the invariants; the screen
 * doesn't need to know.
 */
class UpdateWishlistBookUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data object TitleRequired : Result
        data object IdImmutable : Result
        data object AddedDateImmutable : Result
    }

    suspend operator fun invoke(updated: WishlistBook): Result {
        if (updated.title.isBlank()) return Result.TitleRequired
        val existing = repository.getById(updated.id) ?: return Result.NotFound
        if (existing.id != updated.id) return Result.IdImmutable
        if (existing.addedDate != updated.addedDate) return Result.AddedDateImmutable
        repository.upsert(updated)
        return Result.Success
    }
}
