package dev.khoj.pitaka.data.local.wishlist

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistBookDao {

    /** Default sort (D22-equivalent for wishlist): high priority first, then date-desc. */
    @Query(
        """
        SELECT * FROM wishlist_books
        WHERE purchased = 0
        ORDER BY priority DESC, added_date DESC, id DESC
        """
    )
    fun observeActiveByPriority(): Flow<List<WishlistBookEntity>>

    @Query(
        """
        SELECT * FROM wishlist_books
        WHERE purchased = 0
        ORDER BY added_date DESC, id DESC
        """
    )
    fun observeActiveByRecentlyAdded(): Flow<List<WishlistBookEntity>>

    @Query(
        """
        SELECT * FROM wishlist_books
        WHERE purchased = 1
        ORDER BY purchased_date DESC, id DESC
        """
    )
    fun observePurchased(): Flow<List<WishlistBookEntity>>

    @Query("SELECT * FROM wishlist_books ORDER BY added_date DESC, id DESC")
    fun observeAll(): Flow<List<WishlistBookEntity>>

    @Query(
        """
        SELECT * FROM wishlist_books
        WHERE LOWER(title) LIKE '%' || LOWER(:q) || '%'
           OR (title_transliteration IS NOT NULL AND LOWER(title_transliteration) LIKE '%' || LOWER(:q) || '%')
           OR (author IS NOT NULL AND LOWER(author) LIKE '%' || LOWER(:q) || '%')
           OR (isbn   IS NOT NULL AND LOWER(isbn)   LIKE '%' || LOWER(:q) || '%')
        ORDER BY priority DESC, added_date DESC, id DESC
        """
    )
    fun search(q: String): Flow<List<WishlistBookEntity>>

    @Query("SELECT * FROM wishlist_books WHERE id = :id")
    suspend fun getById(id: Long): WishlistBookEntity?

    @Query("SELECT * FROM wishlist_books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): WishlistBookEntity?

    @Upsert
    suspend fun upsert(book: WishlistBookEntity): Long

    @Query("DELETE FROM wishlist_books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM wishlist_books")
    suspend fun count(): Int
}
