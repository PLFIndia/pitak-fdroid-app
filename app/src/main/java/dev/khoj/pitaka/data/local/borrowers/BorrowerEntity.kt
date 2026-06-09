package dev.khoj.pitaka.data.local.borrowers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Borrower record (pitaka.md §4.1.C). Lives in the encrypted vault.
 *
 * D25: name is required; contact, notes are optional. The repository creates
 * borrower rows inline when the user types a new name in the Lend dialog.
 *
 * No cross-DB FK to books — by design (§4.1.C "logical FK"). The repository
 * layer enforces integrity (D3).
 */
@Entity(
    tableName = "borrowers",
    indices = [
        Index(value = ["name"]),
    ],
)
data class BorrowerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "contact")
    val contact: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,
)
