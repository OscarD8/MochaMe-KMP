package com.mochame.app.data.local

import androidx.sqlite.SQLiteException
import com.mochame.app.domain.exceptions.MochaException
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException


fun Throwable.toMochaException(message: String? = null): MochaException {
    if (this is Error) throw this

    if (this is MochaException) return this

    if (this is TimeoutCancellationException) {
        return MochaException.Transient.Contention(
            message = message,
            cause = this
        )
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