package dev.khoj.pitaka.data.local.borrowers

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query(
        """
        SELECT * FROM loans
        WHERE returned_date IS NULL
        ORDER BY
            CASE WHEN due_date IS NULL THEN 1 ELSE 0 END,
            due_date ASC,
            lent_date DESC,
            id DESC
        """
    )
    fun observeActive(): Flow<List<LoanEntity>>

    @Query(
        """
        SELECT * FROM loans
        WHERE returned_date IS NOT NULL
        ORDER BY returned_date DESC, id DESC
        """
    )
    fun observeReturned(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Query(
        """
        SELECT * FROM loans
        WHERE borrower_id = :borrowerId
        ORDER BY
            CASE WHEN returned_date IS NULL THEN 0 ELSE 1 END,
            COALESCE(returned_date, lent_date) DESC,
            id DESC
        """
    )
    fun observeForBorrower(borrowerId: Long): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE book_id = :bookId")
    suspend fun getAllForBook(bookId: Long): List<LoanEntity>

    @Query(
        """
        SELECT * FROM loans
        WHERE returned_date IS NULL
          AND due_date IS NOT NULL
          AND due_date < :now
        ORDER BY due_date ASC, id ASC
        """
    )
    fun observeOverdue(now: Long): Flow<List<LoanEntity>>

    @Query(
        """
        SELECT * FROM loans
        WHERE returned_date IS NULL
          AND due_date IS NOT NULL
          AND due_date >= :now
          AND due_date < :until
        ORDER BY due_date ASC, id ASC
        """
    )
    fun observeDueSoon(now: Long, until: Long): Flow<List<LoanEntity>>

    @Upsert
    suspend fun upsert(loan: LoanEntity): Long

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM loans WHERE book_id = :bookId")
    suspend fun deleteAllForBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM loans WHERE book_id = :bookId")
    suspend fun countForBook(bookId: Long): Int

    /**
     * Reactive count of ACTIVE (not-yet-returned) loans for a book. Drives the
     * detail screen's "Available = quantity − activeLoans" line. Distinct from
     * [countForBook], which counts ALL loans including returned history (D6).
     */
    @Query("SELECT COUNT(*) FROM loans WHERE book_id = :bookId AND returned_date IS NULL")
    fun observeActiveCountForBook(bookId: Long): Flow<Int>

    @Query(
        """
        SELECT
            COUNT(*) AS totalLoans,
            SUM(CASE WHEN returned_date IS NOT NULL THEN (returned_date - lent_date) ELSE 0 END) AS totalReturnedDurationMs,
            SUM(CASE WHEN returned_date IS NOT NULL THEN 1 ELSE 0 END) AS returnedCount,
            SUM(CASE WHEN due_date IS NOT NULL
                 AND (
                       (returned_date IS NULL AND :now > due_date)
                       OR (returned_date IS NOT NULL AND returned_date > due_date)
                     )
                 THEN 1 ELSE 0 END) AS overdueCount
        FROM loans
        WHERE borrower_id = :borrowerId
        """
    )
    suspend fun statsForBorrower(borrowerId: Long, now: Long): BorrowerStatsRow

    /**
     * Distinct book ids for a borrower — needed when we want to render a
     * borrower's full history with book titles (titles live in books.db,
     * so we cross the boundary in the repository layer).
     */
    @Query("SELECT DISTINCT book_id FROM loans WHERE borrower_id = :borrowerId")
    suspend fun distinctBookIdsForBorrower(borrowerId: Long): List<Long>
}

data class BorrowerStatsRow(
    val totalLoans: Int,
    val totalReturnedDurationMs: Long,
    val returnedCount: Int,
    val overdueCount: Int,
)
