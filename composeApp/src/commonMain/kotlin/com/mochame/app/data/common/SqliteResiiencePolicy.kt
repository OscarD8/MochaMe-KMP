package com.mochame.app.data.common

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import com.mochame.app.domain.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.TimeSource

class SqliteResiliencePolicy(
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
                        logger.i {
                            "Recovered after ${attempt + 1} attempts"
                                .withTimer(mark)
                        }
                    }
                }
            } catch (e: SQLiteException) {
                if (e.isBusy()) {

                    // Log warning only on the first failure to avoid spam
                    if (attempt == 0) logger.w {
                        "Database busy, initiating retry loop"
                            .withTimer(mark)
                    }

                    val jitter = Random.nextLong(0, currentDelay / 2)
                    delay(currentDelay + jitter)
                    currentDelay *= 2
                } else throw e
            }
        }

        return try {
            block()
        } catch (e: Exception) {
            // Critical: Log the exhaustion so the auditor knows WHY the crash happened
            logger.e(e) {
                "Exhausted $MAX_ATTEMPTS retries. Terminal failure."
                    .withTimer(mark)
            }
            throw e
        }
    }

}

// ---- HELPERS ----
/**
 * Check for specific Android-native Exception types if on Android.
 * Fallback to message parsing for KMP-common compatibility.
 */
fun SQLiteException.isBusy(): Boolean {
    val message = this.message?.uppercase() ?: return false

    return message.contains("BUSY") ||
            message.contains("LOCKED") ||
            message.contains("CODE 5") ||
            message.contains("CODE 6")
}