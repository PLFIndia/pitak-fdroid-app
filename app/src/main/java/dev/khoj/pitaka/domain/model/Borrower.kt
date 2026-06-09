package dev.khoj.pitaka.domain.model

data class Borrower(
    val id: Long = 0L,
    val name: String,
    val contact: String? = null,
    val notes: String? = null,
)

data class Loan(
    val id: Long = 0L,
    val bookId: Long,
    val borrowerId: Long,
    val lentDate: Long,
    val dueDate: Long? = null,
    val returnedDate: Long? = null,
    val notes: String? = null,
) {
    val isReturned: Boolean get() = returnedDate != null
    fun isOverdue(now: Long): Boolean =
        !isReturned && dueDate != null && now > dueDate
}

/** D31 — live-computed borrower stats. */
data class BorrowerStats(
    val totalLoans: Int,
    val averageReturnDays: Double?,
    val overdueRate: Double,
)
