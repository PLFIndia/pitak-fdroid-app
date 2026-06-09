package dev.khoj.pitaka.data.local.borrowers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Loan record (pitaka.md §4.1.C).
 *
 * - `borrowerId` has a real Room FK to borrowers (same DB).
 * - `bookId` is a logical FK to books.db; never enforced at the DB layer
 *   (different DB). Repository cleanup handles cross-DB integrity on book
 *   delete (D3).
 * - `returnedDate` null means active; non-null means returned (D6: history
 *   retained indefinitely).
 *
 * Indexes:
 *  - `borrower_id` for "loans for this borrower" queries.
 *  - `book_id` for "is this book currently lent?" lookups.
 *  - `returned_date` for fast active/returned filtering.
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = BorrowerEntity::class,
            parentColumns = ["id"],
            childColumns = ["borrower_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["borrower_id"]),
        Index(value = ["book_id"]),
        Index(value = ["returned_date"]),
    ],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Logical FK to books.db.books.id (no DB-level FK across DBs). */
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "borrower_id")
    val borrowerId: Long,

    @ColumnInfo(name = "lent_date")
    val lentDate: Long,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "returned_date")
    val returnedDate: Long? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,
)
