package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Inject

class AddWishlistBookUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data object TitleRequired : Result
    }

    suspend operator fun invoke(book: WishlistBook): Result {
        if (book.title.isBlank()) return Result.TitleRequired
        val id = repository.upsert(book)
        return Result.Success(id)
    }
}
