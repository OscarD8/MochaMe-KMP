package com.mochame.sync.api.exceptions

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException


/**
 * Handle standard, IO, and Coroutine timeouts.
 *
 * I think I regret this.
 */
fun Throwable.toMochaException(message: String? = null): MochaException {
    if (this is Error) throw this
    if (this is MochaException) return this
    if (this is CancellationException) throw this

    mapToSqliteFailure(message)?.let { throw it }

    return when (this) {
        is TimeoutCancellationException -> MochaException.Transient.Contention(message, this)
        is IOException -> this.mapToIoFailure()
        is IllegalArgumentException, is IllegalStateException -> MochaException.Persistent.StateIssue(message, this)
        else -> MochaException.Persistent.Uncategorized(message, this)
    }
}

private fun Throwable.mapToIoFailure(): MochaException {
    val msg = this.message ?: ""

    return when {
        msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space", ignoreCase = true) ->
            MochaException.Persistent.DiskFull(
                "Storage exhausted during: $msg", this
            )

        msg.contains("Permission", ignoreCase = true) ||
                msg.contains("Access denied", ignoreCase = true) ->
            MochaException.Persistent.StateIssue(
                "Permission denied during: $msg", this
            )

        else -> MochaException.Persistent.StateIssue(
            "IO Error during: $msg", this
        )
    }
}


private fun Throwable.mapToSqliteFailure(message: String?): MochaException? {
    val msg = this.message ?: ""
    return when {
        msg.contains("SQLITE_BUSY", ignoreCase = true) ||
                msg.contains("SQLITE_LOCKED", ignoreCase = true) ||
                msg.contains("database is locked", ignoreCase = true) ->
            MochaException.Transient.VaultBusy(message, this)

        msg.contains("SQLITE_FULL", ignoreCase = true) ||
                msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("no space", ignoreCase = true) ->
            MochaException.Persistent.DiskFull(message ?: "Database write failed: disk full", this)

        msg.contains("SQLITE_CORRUPT", ignoreCase = true) ||
                msg.contains("malformed", ignoreCase = true) ->
            MochaException.Persistent.CorruptionDetected(message ?: "Database corruption detected")

        msg.contains("UNIQUE constraint", ignoreCase = true) ||
                msg.contains("FOREIGN KEY constraint", ignoreCase = true) ->
            MochaException.Persistent.StateIssue(message ?: "Database constraint violation", this)

        else -> null
    }
}