package com.mochame.resonance.infrastructure


import com.mochame.contract.di.IoContext
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.providers.DateTimeProvider
import com.mochame.resonance.data.QuoteEntity
import com.mochame.resonance.data.ResonanceDao
import com.mochame.resonance.data.toDomain
import com.mochame.resonance.data.toEntity
import com.mochame.resonance.domain.Author
import com.mochame.resonance.domain.Book
import com.mochame.resonance.domain.Quote
import com.mochame.resonance.domain.Resonance
import com.mochame.resonance.domain.ResonanceRepository
import com.mochame.resonance.domain.targetResonance
import com.mochame.telemetry.domain.Mood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RoomResonanceRepository(
    private val resonanceDao: ResonanceDao,
    private val dateTimeUtils: DateTimeProvider,
    @IoContext private val ioContext: CoroutineContext
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

    override suspend fun upsertQuote(quote: Quote) = withContext(ioContext) {
        val syncReadyQuote = quote.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertQuote(syncReadyQuote.toEntity())
    }

    override suspend fun deleteQuote(quoteId: String) = withContext(ioContext) {
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

    override suspend fun upsertAuthor(author: Author) = withContext(ioContext) {
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

    override suspend fun archiveBook(bookId: String) = withContext(ioContext) {
        val existing = resonanceDao.getBookById(bookId) ?: return@withContext

        // Use the master clock to refresh the sync heartbeat
        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertBook(archived.toEntity())
    }

    override suspend fun upsertBook(book: Book) = withContext(ioContext) {
        val syncReadyBook = book.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        resonanceDao.upsertBook(syncReadyBook.toEntity())
    }

    override suspend fun deleteBook(bookId: String) = withContext(ioContext) {
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

    override suspend fun deleteAuthor(authorId: String) = withContext(ioContext) {
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