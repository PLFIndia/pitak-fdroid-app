package dev.khoj.pitaka.data.repository

import dev.khoj.pitaka.data.local.wishlist.WishlistBookDao
import dev.khoj.pitaka.data.local.wishlist.toDomain
import dev.khoj.pitaka.data.local.wishlist.toEntity
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.domain.repository.WishlistRepository
import dev.khoj.pitaka.domain.repository.WishlistSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistBookDao,
) : WishlistRepository {

    override fun observeActive(sort: WishlistSort): Flow<List<WishlistBook>> =
        when (sort) {
            WishlistSort.Priority      -> dao.observeActiveByPriority()
            WishlistSort.RecentlyAdded -> dao.observeActiveByRecentlyAdded()
        }.map { list -> list.map { it.toDomain() } }

    override fun observePurchased(): Flow<List<WishlistBook>> =
        dao.observePurchased().map { list -> list.map { it.toDomain() } }

    override fun search(query: String): Flow<List<WishlistBook>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeActive(WishlistSort.Priority)
        return dao.search(trimmed).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: Long): WishlistBook? = dao.getById(id)?.toDomain()

    override suspend fun findByIsbn(isbn: String): WishlistBook? = dao.findByIsbn(isbn)?.toDomain()

    override suspend fun upsert(book: WishlistBook): Long = dao.upsert(book.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
