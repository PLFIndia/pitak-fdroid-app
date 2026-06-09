package dev.khoj.pitaka.data.local.books

import dev.khoj.pitaka.domain.model.Book
import java.util.UUID

/** Entity ↔ domain mapping. Kept boring on purpose. */

internal fun BookEntity.toDomain(): Book = Book(
    id = id,
    bookUid = bookUid,
    title = title,
    titleTransliteration = titleTransliteration,
    author = author,
    isbn = isbn,
    publisher = publisher,
    publishedYear = publishedYear,
    genre = genre,
    coverUrl = coverUrl,
    pageCount = pageCount,
    language = language,
    notes = notes,
    location = location,
    sourceType = sourceType?.let { raw ->
        // Tolerant parse: an unrecognized stored value (e.g. a future enum
        // member read by an older build) maps to null, never crashes.
        runCatching { Book.SourceType.valueOf(raw) }.getOrNull()
    },
    sourceDetail = sourceDetail,
    ageGroup = Book.AgeGroup.fromToken(ageGroup),
    addedDate = addedDate,
    copyCount = copyCount,
    needsMetadata = needsMetadata,
    removed = removed,
    removedAt = removedAt,
    addedBy = addedBy,
)

internal fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    // Mint a stable UUID on first persist; preserve an existing one verbatim
    // (imported/merged rows arrive already carrying their origin device's uid,
    // which MUST be kept so the same logical book reconciles across devices).
    bookUid = bookUid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
    title = title,
    titleTransliteration = titleTransliteration,
    author = author,
    // Unicode-aware lowercase shadow sort keys (D8). Computed here at write time
    // so the Title/Author DAO queries can ORDER BY an indexed column instead of
    // calling LOWER() on every row at query time.
    titleSort = title.trim().lowercase(),
    authorSort = author?.trim()?.lowercase().orEmpty(),
    isbn = isbn,
    publisher = publisher,
    publishedYear = publishedYear,
    genre = genre,
    coverUrl = coverUrl,
    pageCount = pageCount,
    language = language,
    notes = notes,
    location = location,
    sourceType = sourceType?.name,
    sourceDetail = sourceDetail,
    ageGroup = ageGroup?.token,
    addedDate = addedDate,
    copyCount = copyCount,
    needsMetadata = needsMetadata,
    removed = removed,
    removedAt = removedAt,
    addedBy = addedBy,
)
