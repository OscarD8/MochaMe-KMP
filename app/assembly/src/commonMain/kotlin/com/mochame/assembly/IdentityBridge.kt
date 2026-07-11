package com.mochame.assembly

/**
 * The shift to using :core:contract for interfaces has technically made
 * bridging unnecessary, as the sync-engine is no longer designed to be totally
 * decoupled. By adding the engine to another project, you must take the lightweight
 * contract of a local first sync system. Removal of excess interfaces for that
 * project is negligible. With the interface in :core:contract, there can never be
 * a circular dependency. Fixtures pull from the contract and provide fakes, the
 * features themselves can pull their own fixtures, but the fixture fake based on
 * a core contract.
 *
 * Keeping this here as a known alternative.
 */
//@Single(binds = [SyncUserProvider::class])
//class NodeBridge(
//    private val nodeContextManager: DefaultNodeContextManager
//) : SyncUserProvider {
//    override suspend fun getOrEstablishContext(): String {
//        return nodeContextManager.getOrEstablishContext()
//    }
//}