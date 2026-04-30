package com.mochame.contract.node


/**
 * Abstracts persistence logic for node identity and boot state.
 */
interface NodeContextStore {
    suspend fun getNodeId(): String?
    suspend fun saveNodeId(newId: String)
    suspend fun getLastBootedVersion(): Int?
    suspend fun setVersion(version: Int)
    suspend fun hasIdentity(): Boolean
    suspend fun getContext(): NodeContext?
}