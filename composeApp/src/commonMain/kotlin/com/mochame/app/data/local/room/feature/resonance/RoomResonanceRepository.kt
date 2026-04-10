package com.mochame.app.data.local.room.feature.resonance

import com.mochame.app.data.mappers.toDomain
import com.mochame.app.data.mappers.toEntity
import com.mochame.app.data.local.room.dao.ResonanceDao
import com.mochame.app.data.local.room.entities.QuoteEntity
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.feature.resonance.Author
import com.mochame.app.domain.feature.resonance.Book
import com.mochame.app.domain.feature.resonance.Quote
import com.mochame.app.domain.feature.resonance.Resonance
import com.mochame.app.domain.feature.resonance.ResonanceRepository
import com.mochame.app.domain.feature.telemetry.Mood
import com.mochame.app.domain.feature.telemetry.targetResonance
import com.mochame.app.infrastructure.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomResonanceRepository(
    private val resonanceDao: ResonanceDao,
    private val dateTimeUtils: DateTimeUtils,
    private val dispatcher: DispatcherProvider
) : ResonanceRepository {

    // --- QUOTE FETCH ---
    private val signalMutex: Mutex = Mutex()

    override suspend fun getNextSignal(): Quote? = signalMutex.withLock {
        incrementViewAndMap(resonanceDao.getNextSignalCandidate())
    }

    override suspend fun getSignalByEmotion(resonance: Resonance): Quote? =
        signalMutex.withLock {
            incrementViewAndMap(resonanceDao.getQuoteByResonanceCandidate(resonance))
        }

    /**
     * Find resonant quote based on mood. Increment viewCount.
     */
    override suspend fun getResonantQuote(currentMood: Mood): Quote? =
        signalMutex.withLock {
            val target = currentMood.targetResonance

            // Fetch the least-viewed quote in the target resonance
            val quote = resonanceDao.getQuoteByResonanceCandidate(target)

            // Atomic increment to push it to the back of the loop
            quote?.let { incrementViewAndMap(quote) }

            return quote?.toDomain()
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

        resonanceDao.upsertQuote(updated)
        return updated.toDomain()
    }

    override suspend fun upsertQuote(quote: Quote) = withContext(dispatcher.io) {
        val syncReadyQuote = quote.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertQuote(syncReadyQuote.toEntity())
    }

    override suspend fun deleteQuote(quoteId: String) = withContext(dispatcher.io) {
        resonanceDao.deleteQuoteById(quoteId)
    }

    override fun getAuthorFlow(id: String): Flow<Author?> =
        resonanceDao.getAuthorByIdFlow(id).map { it?.toDomain() }

    override fun getBookFlow(id: String): Flow<Book?> =
        resonanceDao.getBookByIdFlow(id).map { it?.toDomain() }

    override fun getQuoteFlow(id: String): Flow<Quote?> =
        resonanceDao.getQuoteByIdFlow(id).map { it?.toDomain() }

    // --- AUTHOR & BOOK MANAGEMENT ---

    override fun getAllAuthors(): Flow<List<Author>> {
        return resonanceDao.getAllAuthorsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertAuthor(author: Author) = withContext(dispatcher.io) {
        val syncReadyAuthor = author.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertAuthor(syncReadyAuthor.toEntity())
    }

    override fun getBooksByAuthor(authorId: String): Flow<List<Book>> {
        return resonanceDao.getBooksByAuthor(authorId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun archiveBook(bookId: String) = withContext(dispatcher.io) {
        val existing = resonanceDao.getBookById(bookId) ?: return@withContext

        // Use the master clock to refresh the sync heartbeat
        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertBook(archived.toEntity())
    }

    override suspend fun upsertBook(book: Book) = withContext(dispatcher.io) {
        val syncReadyBook = book.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertBook(syncReadyBook.toEntity())
    }

    override suspend fun deleteBook(bookId: String) = withContext(dispatcher.io) {
        // 1. The Audit: Count the "Wisdom" inside
        val quoteCount = resonanceDao.getQuoteCountByBook(bookId)

        if (quoteCount > 0) {
            // 2. The Protective Block:
            // We do not recordDelete. We throw an exception that the UI can catch
            // to show a "Warning: This book contains $quoteCount quotes" dialog.
            throw MochaException.SemanticException.Book.InUse(bookId, quoteCount)
        }

        // 3. The Act: Only if it's empty
        resonanceDao.deleteBookById(bookId)
    }

    override suspend fun deleteAuthor(authorId: String) = withContext(dispatcher.io) {
        // Audit check to prevent RESTRICT crash
        val booksByAuthor = resonanceDao.getBooksByAuthorSync(authorId)
        if (booksByAuthor.isNotEmpty()) {
            throw MochaException.SemanticException.Author.InUse(
                authorId,
                booksByAuthor.size
            )
        }
        resonanceDao.deleteAuthorById(authorId)
    }
}