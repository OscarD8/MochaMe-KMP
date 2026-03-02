package com.mochame.app.database.dao

import androidx.room.*
import com.mochame.app.database.entity.AuthorEntity
import com.mochame.app.database.entity.BookEntity
import com.mochame.app.database.entity.QuoteEntity
import com.mochame.app.domain.model.Emotion
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

@Dao
interface SignalDao {

    // --- ATOMIC WISDOM INJECTION ---

    @Transaction
    suspend fun getAndIncrementNextSignal(): QuoteEntity? {
        val quote = getNextSignalQuote() ?: return null
        val updated = quote.copy(
            viewCount = quote.viewCount + 1,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        upsertQuote(updated)
        return quote
    }

    @Transaction
    suspend fun getAndIncrementSignalByEmotion(emotion: Emotion): QuoteEntity? {
        val quote = getQuoteByEmotion(emotion) ?: return null
        val updated = quote.copy(
            viewCount = quote.viewCount + 1,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        upsertQuote(updated)
        return quote
    }

    // --- PRIVATE QUERY HELPERS ---

    @Query("""
        SELECT * FROM (
            SELECT * FROM quotes 
            ORDER BY viewCount ASC 
            LIMIT 5
        ) ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getNextSignalQuote(): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE emotion = :emotion ORDER BY viewCount ASC LIMIT 1")
    suspend fun getQuoteByEmotion(emotion: Emotion): QuoteEntity?

    /**
     * Senior Directive: Replaced @Update with @Upsert for Quotes.
     * This handles both initial insertion and the 'viewCount' increment logic.
     */
    @Upsert
    suspend fun upsertQuote(quote: QuoteEntity)

    // --- AUTHOR & BOOK MANAGEMENT ---

    @Upsert
    suspend fun upsertAuthor(author: AuthorEntity)

    @Upsert
    suspend fun upsertBook(book: BookEntity)

    @Query("SELECT * FROM authors ORDER BY name ASC")
    fun getAllAuthorsFlow(): Flow<List<AuthorEntity>>

    /**
     * Optimized by the index on 'authorId' in BookEntity.
     */
    @Query("SELECT * FROM books WHERE authorId = :authorId")
    fun getBooksByAuthor(authorId: String): Flow<List<BookEntity>>

    // --- ATOMIC DELETIONS (2026 Standard) ---

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)
}