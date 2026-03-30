package com.mochame.app.data.local

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.TimeSource


/**
 * Execution policy for the local database.
 */
class GlobalExecutionPolicy(
    private val logger: Logger
) : ExecutionPolicy {

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_DELAY = 10L
    }

    override suspend fun <R> execute(block: suspend () -> R): R {
        val mark = TimeSource.Monotonic.markNow()
        var currentDelay = INITIAL_DELAY

        repeat(MAX_ATTEMPTS - 1) { attempt ->
            try {
                return block().also {
                    if (attempt > 0) {
                        logger.i { "Recovered after ${attempt + 1} attempts"
                            .withTimer(mark)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                if (e is SQLiteException && e.isVaultLocked()) {
                    if (attempt == 0) logger.w {
                        "Database busy, initiating retry loop".withTimer(mark)
                    }

                    val jitter = Random.nextLong(0, currentDelay / 2)
                    delay(currentDelay + jitter)
                    currentDelay *= 2
                } else {
                    throw e.toMochaException()
                }
            }
        }

        // Final attempt handles the terminal failure
        return try {
            block()
        } catch (e: Exception) {
            val mochaError = e.toMochaException()

            logger.e(e) { "Exhausted $MAX_ATTEMPTS retries".withTimer(mark) +
                    " | ${mochaError.message}"
            }
            throw mochaError
        }
    }
}