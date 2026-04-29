package com.mochame.platform.utils

import androidx.sqlite.SQLiteException
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.exceptions.toMochaException

/**
 * Persistence-specific wrapper that will fall back to the standard wrapper.
 */
fun Throwable.toFullMochaCheck(message: String? = null): MochaException {
    val upperMsg = this.message?.uppercase().orEmpty()

    return when {
        upperMsg.contains("DISK FULL") ->
            MochaException.Persistent.DiskFull(message, this)

        this is SQLiteException -> {
            if (this.isVaultLocked(upperMsg)) {
                MochaException.Transient.VaultBusy(message, this)
            } else {
                MochaException.Persistent.VaultFatal(message, this)
            }
        }

        else -> this.toMochaException(message)
    }
}

private fun Throwable.isVaultLocked(upperMsg: String): Boolean {
    return this is SQLiteException && (
            upperMsg.contains("BUSY") ||
                    upperMsg.contains("LOCKED") ||
                    upperMsg.contains("CODE 5") || // SQLITE_BUSY[cite: 1]
                    upperMsg.contains("CODE 6")    // SQLITE_LOCKED[cite: 1]
            )
}