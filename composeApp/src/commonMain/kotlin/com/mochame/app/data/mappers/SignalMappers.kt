package com.mochame.app.data.mappers

import com.mochame.app.data.local.room.entities.AuthorEntity
import com.mochame.app.data.local.room.entities.BookEntity
import com.mochame.app.data.local.room.entities.QuoteEntity
import com.mochame.app.domain.feature.resonance.Author
import com.mochame.app.domain.feature.resonance.Book
import com.mochame.app.domain.feature.resonance.Quote

// --- AUTHOR MAPPERS ---

internal fun AuthorEntity.toDomain(): Author = Author(
    id = id,
    name = name,
    lastModified = lastModified
)

internal fun Author.toEntity(): AuthorEntity = AuthorEntity(
    id = id,
    name = name,
    lastModified = lastModified
)

// --- BOOK MAPPERS ---

internal fun BookEntity.toDomain(): Book = Book(
    id = id,
    authorId = authorId,
    title = title,
    dateAdded = dateAdded,
    dateFinished = dateFinished,
    isActive = isActive,
    lastModified = lastModified
)

internal fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    authorId = authorId,
    title = title,
    dateAdded = dateAdded,
    dateFinished = dateFinished,
    isActive = isActive,
    lastModified = lastModified
)

// --- QUOTE MAPPERS ---

internal fun QuoteEntity.toDomain(): Quote = Quote(
    id = id,
    bookId = bookId,
    content = content,
    resonance = resonance, // MochaConverters handles the DB-level mapping
    viewCount = viewCount,
    lastModified = lastModified
)

internal fun Quote.toEntity(): QuoteEntity = QuoteEntity(
    id = id,
    bookId = bookId,
    content = content,
    resonance = resonance,
    viewCount = viewCount,
    lastModified = lastModified
)