package com.mochame.platform.policies

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.exceptions.toMochaException
import com.mochame.platform.utils.toFullMochaCheck
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.contract.policy.ExecutionPolicy
import kotlinx.coroutines.delay
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Execution policy for the local database.
 */
@Single(binds = [ExecutionPolicy::class])
class SqliteResiliencePolicy(
    logger: Logger
) : ExecutionPolicy {

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_DELAY = 10L
    }

    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "Exectr"
    )

    override suspend fun <R> execute(operationTag: String, block: suspend () -> R): R {
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
            } catch (e: Exception) {

                val mochaError = e.toFullMochaCheck(message = operationTag)

                if (mochaError is MochaException.Transient.VaultBusy) {
                    if (attempt == 0) logger.w {
                        "Database busy, initiating retry loop".withTimer(mark)
                    }

                    val jitter = Random.Default.nextLong(0, currentDelay / 2)
                    delay(currentDelay + jitter)
                    currentDelay *= 2
                } else {
                    logger.e(e) { "Aborted database interaction. ${e.message}" }
                    throw mochaError
                }
            }
        }
        // Final attempt handles the terminal failure
        return try {
            block()
        } catch (e: Exception) {
            val finalError = e.toMochaException()

            logger.e(e) {
                "$operationTag. Exhausted $MAX_ATTEMPTS retries".withTimer(mark) +
                        " | ${finalError.message}"
            }
            throw finalError
        }
    }
}