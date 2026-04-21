package com.mochame.utils.exceptions

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException


fun Throwable.toMochaException(message: String? = null): MochaException {
    if (this is Error) throw this

    if (this is MochaException) return this

    if (this is TimeoutCancellationException) {
        return MochaException.Transient.Contention(message = message, cause = this)
    }

     if ( this is IOException) {
        val msg = this.message ?: ""

        when {
            // Common markers for disk exhaustion across platforms
            msg.contains("ENOSPC", ignoreCase = true) ||
                    msg.contains("No space", ignoreCase = true) ->
                MochaException.Persistent.DiskFull(
                    "Storage exhausted during $message: $msg", this
                )

            // Permission or missing directory issues
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

    if (this is CancellationException) throw this

    return when {
        // TRANSIENT
        this.isVaultLocked() ->
            MochaException.Transient.VaultBusy(message, this)

        // PERSISTENT
        this is IllegalArgumentException ->
            MochaException.Persistent.VaultFatal(message, this)

        this is IllegalStateException ->
            MochaException.Persistent.VaultFatal(message, this)

        this.message?.uppercase()?.contains("DISK FULL") == true ->
            MochaException.Persistent.DiskFull(message, this)

        else -> MochaException.Persistent.Uncategorized(message, this)
    }
}

fun Throwable.isVaultLocked(): Boolean {
    val msg = this.message?.uppercase() ?: ""
    return this is SQLiteException && (
            msg.contains("BUSY") ||
                    msg.contains("LOCKED") ||
                    msg.contains("CODE 5") || // SQLITE_BUSY
                    msg.contains("CODE 6")    // SQLITE_LOCKED
            )
}