package com.mochame.app.data.local.room.utils

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Designed to enable a DML action blocked by a concurrent heavy IO operation
 * to retry without killing the app.
 */
suspend fun <R> runWithRetry(
    maxAttempts: Int = 5,
    initialDelay: Long = 10L,
    block: suspend () -> R
): R {
    var currentDelay = initialDelay
    repeat(maxAttempts - 1) { attempt ->
        try {
            return block()
        } catch (e: SQLiteException) {
            val isBusy = e.message?.contains("BUSY", ignoreCase = true) == true ||
                    e.message?.contains("LOCKED", ignoreCase = true) == true

            if (isBusy) {
                // Add jitter to prevent "Thundering Herds"
                val jitter = Random.nextLong(0, currentDelay / 2)
                delay(currentDelay + jitter)
                currentDelay *= 2
            } else {
                throw e
            }
        }
    }
    return block()
}