@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.khoj.pitaka.data.repository

import dev.khoj.pitaka.data.local.borrowers.toDomain
import dev.khoj.pitaka.data.local.borrowers.toEntity
import dev.khoj.pitaka.data.vault.VaultLockedException
import dev.khoj.pitaka.data.vault.VaultSession
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.repository.BorrowerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All reads observe the session — when the vault locks, the underlying DB
 * closes and the flows yield an empty list (the UI screen has already
 * navigated away by then).
 *
 * Writes throw [VaultLockedException] if called while locked. UseCases
 * convert that to a vault-locked result; the UI prompts unlock.
 */
@Singleton
class BorrowerRepositoryImpl @Inject constructor(
    private val session: VaultSession,
) : BorrowerRepository {

    override fun observeAll(): Flow<List<Borrower>> = session.state.flatMapLatest { s ->
        val db = (s as? dev.khoj.pitaka.data.vault.VaultState.Unlocked)?.database
            ?: return@flatMapLatest flowOf(emptyList())
        db.borrowerDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override fun search(query: String): Flow<List<Borrower>> = session.state.flatMapLatest { s ->
        val db = (s as? dev.khoj.pitaka.data.vault.VaultState.Unlocked)?.database
            ?: return@flatMapLatest flowOf(emptyList())
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            db.borrowerDao().observeAll().map { list -> list.map { it.toDomain() } }
        } else {
            db.borrowerDao().search(trimmed).map { list -> list.map { it.toDomain() } }
        }
    }

    override suspend fun getById(id: Long): Borrower? =
        unlockedDao().getById(id)?.toDomain()

    override suspend fun findByName(name: String): Borrower? =
        unlockedDao().findByName(name)?.toDomain()

    override suspend fun upsert(borrower: Borrower): Long =
        unlockedDao().upsert(borrower.toEntity())

    override suspend fun delete(id: Long) {
        unlockedDao().deleteById(id)
    }

    private fun unlockedDao() =
        session.currentDatabase()?.borrowerDao() ?: throw VaultLockedException()
}
