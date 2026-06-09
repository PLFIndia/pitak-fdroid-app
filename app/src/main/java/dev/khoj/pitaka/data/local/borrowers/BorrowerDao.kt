package dev.khoj.pitaka.data.local.borrowers

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BorrowerDao {

    @Query("SELECT * FROM borrowers ORDER BY LOWER(name) ASC, id ASC")
    fun observeAll(): Flow<List<BorrowerEntity>>

    @Query(
        """
        SELECT * FROM borrowers
        WHERE LOWER(name) LIKE '%' || LOWER(:q) || '%'
           OR (contact IS NOT NULL AND LOWER(contact) LIKE '%' || LOWER(:q) || '%')
        ORDER BY LOWER(name) ASC, id ASC
        """
    )
    fun search(q: String): Flow<List<BorrowerEntity>>

    @Query("SELECT * FROM borrowers WHERE id = :id")
    suspend fun getById(id: Long): BorrowerEntity?

    @Query("SELECT * FROM borrowers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): BorrowerEntity?

    @Upsert
    suspend fun upsert(borrower: BorrowerEntity): Long

    @Query("DELETE FROM borrowers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM borrowers")
    suspend fun count(): Int
}
