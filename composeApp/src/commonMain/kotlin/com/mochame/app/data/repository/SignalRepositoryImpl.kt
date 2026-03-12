package com.mochame.app.data.repository

import com.mochame.app.core.AuthorInUseException
import com.mochame.app.core.BookInUseException
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.SignalDao
import com.mochame.app.database.entity.QuoteEntity
import com.mochame.app.domain.model.Author
import com.mochame.app.domain.model.Book
import com.mochame.app.domain.model.Resonance
import com.mochame.app.domain.model.Quote
import com.mochame.app.domain.model.telemetry.Mood
import com.mochame.app.domain.model.telemetry.targetResonance
import com.mochame.app.domain.repository.SignalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SignalRepositoryImpl(
    private val signalDao: SignalDao,
    private val dateTimeUtils: DateTimeUtils
) : SignalRepository {

    // --- QUOTE FETCH ---
    private val signalMutex: Mutex = Mutex()

    override suspend fun getNextSignal(): Quote? = signalMutex.withLock {
        incrementViewAndMap(signalDao.getNextSignalCandidate())
    }

    override suspend fun getSignalByEmotion(resonance: Resonance): Quote? = signalMutex.withLock {
        incrementViewAndMap(signalDao.getQuoteByResonanceCandidate(resonance))
    }

    /**
     * Find resonant quote based on mood. Increment viewCount.
     */
    override suspend fun getResonantQuote(currentMood: Mood): Quote? = signalMutex.withLock {
        val target = currentMood.targetResonance

        // Fetch the least-viewed quote in the target resonance
        val quote = signalDao.getQuoteByResonanceCandidate(target)

        // Atomic increment to push it to the back of the loop
        quote?.let { incrementViewAndMap(quote) }

        return quote as Quote?
    }

    /**
     * A private helper that handles the transition from a raw entity to a
     * used/viewed domain model.
     */
    private suspend fun incrementViewAndMap(candidate: QuoteEntity?): Quote? {
        if (candidate == null) return null

        val updated = candidate.copy(
            viewCount = candidate.viewCount + 1,
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )

        signalDao.upsertQuote(updated)
        return updated.toDomain()
    }

    override suspend fun upsertQuote(quote: Quote) = withContext(Dispatchers.IO) {
        val syncReadyQuote = quote.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        signalDao.upsertQuote(syncReadyQuote.toEntity())
    }

    override suspend fun deleteQuote(quoteId: String) = withContext(Dispatchers.IO) {
        signalDao.deleteQuoteById(quoteId)
    }

    override fun getAuthorFlow(id: String): Flow<Author?> =
        signalDao.getAuthorByIdFlow(id).map { it?.toDomain() }

    override fun getBookFlow(id: String): Flow<Book?> =
        signalDao.getBookByIdFlow(id).map { it?.toDomain() }

    override fun getQuoteFlow(id: String): Flow<Quote?> =
        signalDao.getQuoteByIdFlow(id).map { it?.toDomain() }

    // --- AUTHOR & BOOK MANAGEMENT ---

    override fun getAllAuthors(): Flow<List<Author>> {
        return signalDao.getAllAuthorsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertAuthor(author: Author) = withContext(Dispatchers.IO) {
        val syncReadyAuthor = author.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        signalDao.upsertAuthor(syncReadyAuthor.toEntity())
    }

    override fun getBooksByAuthor(authorId: String): Flow<List<Book>> {
        return signalDao.getBooksByAuthor(authorId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun archiveBook(bookId: String) = withContext(Dispatchers.IO) {
        val existing = signalDao.getBookById(bookId) ?: return@withContext

        // Use the master clock to refresh the sync heartbeat
        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        signalDao.upsertBook(archived.toEntity())
    }

    override suspend fun upsertBook(book: Book) = withContext(Dispatchers.IO) {
        val syncReadyBook = book.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        signalDao.upsertBook(syncReadyBook.toEntity())
    }

    override suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        // 1. The Audit: Count the "Wisdom" inside
        val quoteCount = signalDao.getQuoteCountByBook(bookId)

        if (quoteCount > 0) {
            // 2. The Protective Block:
            // We do not delete. We throw an exception that the UI can catch
            // to show a "Warning: This book contains $quoteCount quotes" dialog.
            throw BookInUseException(bookId, quoteCount)
        }

        // 3. The Act: Only if it's empty
        signalDao.deleteBookById(bookId)
    }

    override suspend fun deleteAuthor(authorId: String) = withContext(Dispatchers.IO) {
        // Audit check to prevent RESTRICT crash
        val booksByAuthor = signalDao.getBooksByAuthorSync(authorId)
        if (booksByAuthor.isNotEmpty()) {
            throw AuthorInUseException(authorId, booksByAuthor.size)
        }
        signalDao.deleteAuthorById(authorId)
    }
}