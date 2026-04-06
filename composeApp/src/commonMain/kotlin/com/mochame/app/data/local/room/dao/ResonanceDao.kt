package com.mochame.app.data.local.room.dao

import androidx.room.*
import com.mochame.app.data.local.room.entities.AuthorEntity
import com.mochame.app.data.local.room.entities.BookEntity
import com.mochame.app.data.local.room.entities.QuoteEntity
import com.mochame.app.domain.feature.resonance.Resonance
import kotlinx.coroutines.flow.Flow

@Dao
interface ResonanceDao {

    // --- QUOTES (READS) ---

    /**
     * Finds a candidate quote for a signal.
     * Picks from the 5 least-seen quotes and randomizes the result.
     */
    @Query("""
        SELECT * FROM (
            SELECT * FROM quotes 
            ORDER BY viewCount ASC 
            LIMIT 5
        ) ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getNextSignalCandidate(): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE resonance = :resonance ORDER BY viewCount ASC LIMIT 1")
    suspend fun getQuoteByResonanceCandidate(resonance: Resonance): QuoteEntity?

    // --- PERSISTENCE ---

    @Upsert
    suspend fun upsertQuote(quote: QuoteEntity)

    @Upsert
    suspend fun upsertAuthor(author: AuthorEntity)

    @Upsert
    suspend fun upsertBook(book: BookEntity)

    // --- OBSERVATION FLOWS ---

    @Query("SELECT * FROM authors ORDER BY name ASC")
    fun getAllAuthorsFlow(): Flow<List<AuthorEntity>>

    @Query("SELECT COUNT(*) FROM quotes WHERE bookId = :bookId")
    suspend fun getQuoteCountByBook(bookId: String): Int

    @Query("SELECT * FROM quotes WHERE id = :id LIMIT 1")
    fun getQuoteByIdFlow(id: String): Flow<QuoteEntity?>

    @Query("SELECT * FROM books WHERE authorId = :authorId")
    fun getBooksByAuthor(authorId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: String): BookEntity?

    /**
     * Observes a specific Author by their unique identifier.
     * Returns a Flow that emits null if the author does not exist.
     */
    @Query("SELECT * FROM authors WHERE id = :id LIMIT 1")
    fun getAuthorByIdFlow(id: String): Flow<AuthorEntity?>

    /**
     * One-shot retrieval of books by author for deletion auditing.
     * Used by the SignalRepository to prevent RESTRICT crashes.
     */
    @Query("SELECT * FROM books WHERE authorId = :authorId")
    suspend fun getBooksByAuthorSync(authorId: String): List<BookEntity>

    /**
     * Observes a specific Book by its unique identifier.
     * Returns a Flow that emits null if the book does not exist.
     */
    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookByIdFlow(id: String): Flow<BookEntity?>

    // --- ATOMIC DELETIONS ---

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("DELETE FROM authors WHERE id = :id")
    suspend fun deleteAuthorById(id: String)

    @Query("DELETE FROM quotes WHERE bookId = :bookId")
    suspend fun deleteQuotesByBookId(bookId: String)
}

