package com.mochame.app.core

enum class SyncStatus(val id: Int) {
    /** * Ledger: Record is waiting in the outbox.
     * Metadata: Module is doing nothing.
     */
    PENDING(0),

    /** * Ledger: Record is currently being uploaded.
     * Metadata: Network session is active (Master Lock held).
     */
    SYNCING(1),

    /** * Ledger: Record has been ACKed by the server.
     * Metadata: The last session was a success.
     */
    SUCCESS(2),

    /** * Metadata Only: The last session crashed or returned an error.
     */
    FAILED(3);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: PENDING
    }
}

enum class MutationOp(val id: Int) {
    UPSERT(0),
    DELETE(1);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: UPSERT
    }
}


sealed interface MochaModule {
    val tag: String

    data object Bio : MochaModule { override val tag = "BIO" }
    data object Signal : MochaModule { override val tag = "SIGNAL" }
    data object Telemetry : MochaModule { override val tag = "TELEMETRY" }
}
