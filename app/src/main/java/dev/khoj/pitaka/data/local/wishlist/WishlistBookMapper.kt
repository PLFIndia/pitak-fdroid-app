package dev.khoj.pitaka.data.local.wishlist

import dev.khoj.pitaka.domain.model.WishlistBook

internal fun WishlistBookEntity.toDomain(): WishlistBook = WishlistBook(
    id = id,
    title = title,
    titleTransliteration = titleTransliteration,
    author = author,
    isbn = isbn,
    publisher = publisher,
    publishedYear = publishedYear,
    coverUrl = coverUrl,
    priceEstimate = priceEstimate,
    priority = priority,
    notes = notes,
    source = runCatching { WishlistBook.Source.valueOf(source) }
        .getOrDefault(WishlistBook.Source.MANUAL),
    addedDate = addedDate,
    purchased = purchased,
    purchasedDate = purchasedDate,
    needsMetadata = needsMetadata,
)

internal fun WishlistBook.toEntity(): WishlistBookEntity = WishlistBookEntity(
    id = id,
    title = title,
    titleTransliteration = titleTransliteration,
    author = author,
    isbn = isbn,
    publisher = publisher,
    publishedYear = publishedYear,
    coverUrl = coverUrl,
    priceEstimate = priceEstimate,
    priority = priority,
    notes = notes,
    source = source.name,
    addedDate = addedDate,
    purchased = purchased,
    purchasedDate = purchasedDate,
    needsMetadata = needsMetadata,
)
