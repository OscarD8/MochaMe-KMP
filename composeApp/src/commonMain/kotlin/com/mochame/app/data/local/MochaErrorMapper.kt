package com.mochame.app.data.local

import androidx.sqlite.SQLiteException
import com.mochame.app.domain.exceptions.MochaException
import kotlin.coroutines.cancellation.CancellationException


fun Throwable.toMochaException(): MochaException {
    if (this is Error) throw this

    if (this is CancellationException) throw this

    // 3. Prevent Double-Wrapping:
    if (this is MochaException) return this

    val msg = this.message?.uppercase() ?: "UNEXPECTED SYSTEM FAILURE"

    return when {
        this is IllegalArgumentException ->
            MochaException.Persistent.VaultFatal("Logic Error: $msg", this)

        this is IllegalStateException ->
            MochaException.Persistent.VaultFatal("State Mismatch: $msg", this)

        this.isVaultLocked() ->
            MochaException.Transient.VaultBusy(this)

        msg.contains("FULL") ->
            MochaException.Persistent.DiskFull(this)

        else -> MochaException.Persistent.VaultFatal(this.message ?: "Vault Failure", this)
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