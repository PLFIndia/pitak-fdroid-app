package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import javax.inject.Inject

class AddBorrowerUseCase @Inject constructor(private val repo: BorrowerRepository) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data object NameRequired : Result
        data object VaultLocked : Result
    }
    suspend operator fun invoke(borrower: Borrower): Result {
        if (borrower.name.isBlank()) return Result.NameRequired
        return try {
            Result.Success(repo.upsert(borrower))
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        }
    }
}

class UpdateBorrowerUseCase @Inject constructor(private val repo: BorrowerRepository) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data object NameRequired : Result
        data object VaultLocked : Result
    }
    suspend operator fun invoke(borrower: Borrower): Result {
        if (borrower.name.isBlank()) return Result.NameRequired
        return try {
            val existing = repo.getById(borrower.id) ?: return Result.NotFound
            repo.upsert(borrower.copy(id = existing.id))
            Result.Success
        } catch (e: VaultLockedException) {
            Result.VaultLocked
        }
    }
}

class DeleteBorrowerUseCase @Inject constructor(private val repo: BorrowerRepository) {
    sealed interface Result {
        data object Success : Result
        data object VaultLocked : Result
        data class Failed(val cause: Throwable) : Result
    }
    suspend operator fun invoke(id: Long): Result = try {
        repo.delete(id)
        Result.Success
    } catch (e: VaultLockedException) {
        Result.VaultLocked
    } catch (t: Throwable) {
        Result.Failed(t)
    }
}
