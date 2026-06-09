package dev.khoj.pitaka.data.local.borrowers

import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.domain.model.Loan

internal fun BorrowerEntity.toDomain(): Borrower = Borrower(
    id = id, name = name, contact = contact, notes = notes,
)

internal fun Borrower.toEntity(): BorrowerEntity = BorrowerEntity(
    id = id, name = name, contact = contact, notes = notes,
)

internal fun LoanEntity.toDomain(): Loan = Loan(
    id = id,
    bookId = bookId,
    borrowerId = borrowerId,
    lentDate = lentDate,
    dueDate = dueDate,
    returnedDate = returnedDate,
    notes = notes,
)

internal fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    bookId = bookId,
    borrowerId = borrowerId,
    lentDate = lentDate,
    dueDate = dueDate,
    returnedDate = returnedDate,
    notes = notes,
)
