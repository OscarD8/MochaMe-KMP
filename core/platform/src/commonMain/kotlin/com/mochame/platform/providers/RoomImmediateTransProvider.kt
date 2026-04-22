package com.mochame.platform.providers

import androidx.room.RoomDatabase
import androidx.room.Transactor
import androidx.room.useWriterConnection
import org.koin.core.annotation.Single

@Single(binds = [TransactionProvider::class])
class RoomImmediateTransProvider(private val db: RoomDatabase) : TransactionProvider {
    override suspend fun <R> runImmediateTransaction(block: suspend () -> R): R {
        return db.useWriterConnection { conn ->
            conn.withTransaction(type = Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
        }
    }
}