package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.domain.repository.WishlistRepository
import javax.inject.Inject

class DeleteWishlistBookUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
