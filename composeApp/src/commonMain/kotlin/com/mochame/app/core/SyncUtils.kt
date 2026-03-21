package com.mochame.app.core

enum class SyncStatus(val id: Int) {
    /** * Metadata: Everything is in sync. No work to do.
     */
    IDLE(0),

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


enum class MochaModule(val tag: String) {
    BIO("bio"),
    SIGNAL("signal"),
    TELEMETRY("telemetry");

    companion object {
        val allTags get() = entries.map { it.tag }
    }
}