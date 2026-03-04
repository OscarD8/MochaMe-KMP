package com.mochame.app.domain.repository

import com.mochame.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SignalRepository {

    suspend fun getNextSignal(): Quote?

    suspend fun getSignalByEmotion(emotion: Emotion): Quote?

    suspend fun upsertQuote(quote: Quote)

    suspend fun deleteQuote(quoteId: String)


    fun getAuthorFlow(id: String): Flow<Author?>

    fun getBookFlow(id: String): Flow<Book?>

    fun getQuoteFlow(id: String): Flow<Quote?>

    // --- AUTHOR & BOOK MANAGEMENT ---

    fun getAllAuthors(): Flow<List<Author>>

    suspend fun upsertAuthor(author: Author)

    fun getBooksByAuthor(authorId: String): Flow<List<Book>>

    suspend fun upsertBook(book: Book)

    suspend fun deleteBook(bookId: String)

    suspend fun archiveBook(bookId: String)

    suspend fun deleteAuthor(authorId: String)
}