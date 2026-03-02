package com.mochame.app.data.repository

import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.SignalDao
import com.mochame.app.domain.model.Author
import com.mochame.app.domain.model.Book
import com.mochame.app.domain.model.Emotion
import com.mochame.app.domain.model.Quote
import com.mochame.app.domain.repository.SignalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class SignalRepositoryImpl(
    private val signalDao: SignalDao
) : SignalRepository {

    // --- THE INFINITE LOOP (Wisdom Injection) ---

    override suspend fun getNextSignal(): Quote? = withContext(Dispatchers.IO) {
        // Atomic fetch-and-increment handled by the DAO Transaction
        signalDao.getAndIncrementNextSignal()?.toDomain()
    }

    override suspend fun getSignalByEmotion(emotion: Emotion): Quote? = withContext(Dispatchers.IO) {
        // Consistency Fix: Semantic hits also increment viewCount
        signalDao.getAndIncrementSignalByEmotion(emotion)?.toDomain()
    }

    override suspend fun upsertQuote(quote: Quote) = withContext(Dispatchers.IO) {
        val syncReadyQuote = quote.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        signalDao.upsertQuote(syncReadyQuote.toEntity())
    }

    override suspend fun deleteQuote(quoteId: String) = withContext(Dispatchers.IO) {
        signalDao.deleteQuoteById(quoteId)
    }

    // --- AUTHOR & BOOK MANAGEMENT ---

    override fun getAllAuthors(): Flow<List<Author>> {
        return signalDao.getAllAuthorsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertAuthor(author: Author) = withContext(Dispatchers.IO) {
        val syncReadyAuthor = author.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        signalDao.upsertAuthor(syncReadyAuthor.toEntity())
    }

    override fun getBooksByAuthor(authorId: String): Flow<List<Book>> {
        return signalDao.getBooksByAuthor(authorId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertBook(book: Book) = withContext(Dispatchers.IO) {
        val syncReadyBook = book.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        signalDao.upsertBook(syncReadyBook.toEntity())
    }

    override suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        // Atomic deletion; FK CASCADE in SQLite cleans up associated Quotes
        signalDao.deleteBookById(bookId)
    }
}