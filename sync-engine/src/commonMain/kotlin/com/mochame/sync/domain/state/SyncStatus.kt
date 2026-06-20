package com.mochame.sync.domain.state

enum class SyncStatus(val id: Int) {
    /** * Metadata: Everything is in sync. No work to do.
     */
    IDLE(0),

    /** * Ledger: Record is waiting in the outbox.
     * Metadata: Module is doing nothing.
     */
    PENDING(1),

    /** * Ledger: Record is currently being uploaded.
     * Metadata: Network session is active (Master Lock held).
     */
    SYNCING(2),

    /** * Ledger: Record has been ACKed by the server.
     * Metadata: The last session was a success.
     */
    SUCCESS(3),

    /** * Metadata Only: The last session crashed or returned an error.
     */
    FAILED(4),

    /** Incoming payloads set this status via the codecs decoding methods to ensure they
     * are categorically distinct from outgoing intent processing. */
    RECEIVED(5),

    /**
     *A quarantined intent is one the system has given up on delivering automatically. It represents a mutation that the local device made, that has a valid hlc and a valid payload, but that has failed to reach the server enough times that automatic retry is no longer safe to assume will succeed.
     * The causes are typically: the payload is somehow malformed in a way the server rejects consistently, the server has a bug processing this specific model or operation, or the local data got into an inconsistent state that the encoder captured faithfully but the server cannot accept.
     * Once quarantined, the Janitor does not reset it, the coordinator does not claim it, and observePendingCount does not count it — that query filters on syncStatus = PENDING only. The intent sits in the table silently until explicitly handled.
     */
    QUARANTINED(6);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: PENDING
    }
}