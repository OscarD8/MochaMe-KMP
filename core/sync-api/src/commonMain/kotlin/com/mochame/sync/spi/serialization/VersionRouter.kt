package com.mochame.sync.spi.serialization

import com.mochame.sync.api.exceptions.MochaException


/*
 * Notes: Protobuf seems to only accept ByteArrays for each encoding/decoding step. This necessitates
 * that at each stage of the unwrapping/wrapping lifecycle for sync metadata handling, I have to provide the
 * BatchPayload, individual intents, and their wrapped payloads, all as ByteArrays at each step.
 * To make this worse, these arrays need to have their version stripped/prepended. This means that
 * for each serialized model, version stripping/prepending is resulting in an entire copy of the payload.
 * It looks like you can make the Intent level take a defined PayLoad abstract type that each feature model implements,
 * meaning it can decode the wrapped generic payload and possibly handle versioning here, resulting in one less total
 * payload copy. This would probably result in coupling though, completely affecting the current Gradle setup
 * and making features implement extra steps. Something else to consider is a different tool altogether,
 * one that may allow usage of sinks/sources for encoding/decoding, so that the entire chain of data processing can be
 * performed on a single Buffer with zero copying?
 *
 * For now, I will see how the current pure ByteArray usage performs, considering this appears to be somewhat
 * intended with Protobuf, and it will only be working with SQLite data that is effectively just UI state,
 * with larger binary files being represented by an overFlowBlobId - this at least, is where the current flow diverts.
 * Another benefit being that the :sync-engine module is totally decoupled in the current design.
 */

/**
 * Basic contract for all version control functionality.
 *
 * @param T Type of object assigned to a version on the [versionRegistry]
 * @property latestVersion assigned to a [latestCodec] as its key.
 * @property versionRegistry maps a byte value representing a version to whatever Type is provided.
 */
interface VersionRouter<T : Any> {
    val latestVersion: Int

    /**
     * Nullable in case version 1 is the starting point, in which case index 0 is to be null.
     */
    val versionRegistry: Array<T?>
}

/**
 * Property initializers in interfaces are prohibited. Interfaces are just contracts.
 * Therefore, Classes that want basic version routing mapping dont need to extend a
 * base class that implements an interface. They simply implement the interface directly, become
 * the type, and as this extension property has a receiver type of that type
 * , the class gets implicit uniform access to the latest codec without having
 * to repeat logic, or depend on a tool such as VersionRoutingUtils.getLatestCodec().
 *
 * @param T The Type for the object that is being version controlled and fetched.
 * @throws MochaException.Persistent.CorruptionDetected If the version is not an index
 */
val <T : Any> VersionRouter<T>.latestCodec: T
    get() {
        return versionRegistry.getOrNull(latestVersion)
            ?: throw MochaException.Persistent.UnknownProtocolVersion(latestVersion)
    }

fun <T : Any> VersionRouter<T>.getCodec(version: Byte): T {
    val index = (version.toInt() and 0xFF)
    return versionRegistry.getOrNull(index)
        ?: throw MochaException.Persistent.UnknownProtocolVersion(latestVersion)
}

fun <T : Any> VersionRouter<T>.getCodec(version: Int): T {
    return versionRegistry.getOrNull(version)
        ?: throw MochaException.Persistent.UnknownProtocolVersion(latestVersion)
}
