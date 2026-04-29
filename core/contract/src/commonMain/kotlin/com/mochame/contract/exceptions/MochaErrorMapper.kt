package com.mochame.contract.exceptions

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException


/**
 * Handle standard, IO, and Coroutine timeouts.
 */
fun Throwable.toMochaException(message: String? = null): MochaException {
    if (this is Error) throw this
    if (this is MochaException) return this
    if (this is CancellationException) throw this


    return when (this) {
        is TimeoutCancellationException -> MochaException.Transient.Contention(message, this)
        is IOException -> this.mapToIoFailure(message)
        is IllegalArgumentException, is IllegalStateException -> MochaException.Persistent.VaultFatal(message, this)
        else -> MochaException.Persistent.Uncategorized(message, this)
    }
}

private fun Throwable.mapToIoFailure(message: String?): MochaException {
    val msg = this.message ?: ""

    return when {
        msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space", ignoreCase = true) ->
            MochaException.Persistent.DiskFull(
                "Storage exhausted during $message: $msg", this
            )

        msg.contains("Permission", ignoreCase = true) ||
                msg.contains("Access denied", ignoreCase = true) ->
            MochaException.Persistent.VaultFatal(
                "Permission denied during $message: $msg", this
            )

        else -> MochaException.Persistent.VaultFatal(
            "IO Error during $message: $msg", this
        )
    }
}



