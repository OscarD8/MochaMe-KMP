package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException

/**
 * Universal base class representing the contract of a version-routed codec registry.
 * Houses shared properties and validates registry setup at initialization.
 */
open class VersionRoutingCodec<TCodec : Any>(
    protected val codecMap: Map<Byte, TCodec>,
    protected val latestVersion: Byte,
    protected val logger: Logger
) {
    val latestCodec: TCodec = codecMap[latestVersion] ?: run {
        logger.e { "Codec Map for TCodec version fetching not setup correctly. Check property setup." }
        throw MochaException.Persistent.CorruptionDetected("No codec registered for version $latestVersion")
    }
}
