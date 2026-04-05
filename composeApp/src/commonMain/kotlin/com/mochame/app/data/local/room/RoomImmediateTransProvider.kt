package com.mochame.app.data.local.room

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.mochame.app.domain.sync.TransactionProvider

class RoomImmediateTransProvider(private val db: MochaDatabase) : TransactionProvider {
    override suspend fun <R> runImmediateTransaction(block: suspend () -> R): R {
        return db.useWriterConnection { conn ->
            conn.withTransaction(type = Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
        }
    }
}