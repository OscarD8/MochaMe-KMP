package com.mochame.app.domain.repository

import com.mochame.app.domain.model.Author
import com.mochame.app.domain.model.Book
import com.mochame.app.domain.model.Quote
import com.mochame.app.domain.model.Emotion
import kotlinx.coroutines.flow.Flow

interface SignalRepository {

    // --- THE INFINITE LOOP (Wisdom Injection) ---

    /**
     * Fetches the next quote for display using the "Infinite Loop" logic.
     * The implementation must automatically increment the viewCount and
     * update the lastModified timestamp upon retrieval.
     */
    suspend fun getNextSignal(): Quote?

    /**
     * Fetches a quote tailored to a specific emotional state.
     * Used for the Phase 4 "Semantic Blend" AI features.
     */
    suspend fun getSignalByEmotion(emotion: Emotion): Quote?

    /**
     * Persists or updates a quote in the local-first archive.
     */
    suspend fun upsertQuote(quote: Quote)

    /**
     * Atomic deletion of a quote by its unique ID.
     */
    suspend fun deleteQuote(quoteId: String)

    // --- AUTHOR & BOOK MANAGEMENT ---

    /**
     * Reactive stream of all authors, sorted alphabetically.
     */
    fun getAllAuthors(): Flow<List<Author>>

    /**
     * Persists or updates an author.
     */
    suspend fun upsertAuthor(author: Author)

    /**
     * Reactive stream of books associated with a specific author.
     */
    fun getBooksByAuthor(authorId: String): Flow<List<Book>>

    /**
     * Persists or updates a book entry.
     */
    suspend fun upsertBook(book: Book)

    /**
     * Atomic deletion of a book. Triggers CASCADE to quotes.
     */
    suspend fun deleteBook(bookId: String)
}