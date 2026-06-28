package com.mochame.sync.contract

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import kotlinx.io.Source


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
    val latestVersion: Byte

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
        val index = (latestVersion.toInt() and 0xFF)
        return versionRegistry.getOrNull(index)
            ?: throw MochaException.Persistent.UnknownProtocolVersion(latestVersion)
    }

fun <T : Any> VersionRouter<T>.getCodec(version: Byte): T {
    val index = (version.toInt() and 0xFF)
    return versionRegistry.getOrNull(index)
        ?: throw MochaException.Persistent.UnknownProtocolVersion(latestVersion)
}

/**
 * Establishes a new ByteArray, appending the version before utilizing [ByteArray.copyInto] on the
 * result from [block]. Note that any lambda parameter (like [block]) is implicitly
 * inline as well.
 *
 * @return ByteArray the result of the execution performed, appended with the version
 * at index 0.
 * @throws MochaException.Persistent.CorruptionDetected if [block] failed to compute a
 * returning ByteArray
 */
inline fun prependVersionTo(
    version: Byte,
    logger: Logger,
    block: () -> ByteArray?
): ByteArray {
    val payload = block() ?: run {
        logger.e { "Cannot prepend version. Provided execution block returned a null ByteArray." }
        throw MochaException.Persistent.CorruptionDetected("Cannot prepend version. Execution returned null ByteArray.")
    }

    logger.d { "Encoding processed | Size: ${payload.size} bytes | Version Header: $version" }

    val envelope = ByteArray(1 + payload.size)
    envelope[0] = version
    payload.copyInto(envelope, destinationOffset = 1)
    return envelope
}

/**
 * Utilizes [versionRegistry] to provide [block]
 * as an anonymous lambda expression passing through [T] alongside the stripped [bytes].
 *
 * In the intended use case, all version routing methods call this method, and
 * therefore check bytes exist, strip the version, map the codec, and proceed
 * with their own codec usage via [block]. Block is implicitely inline.
 *
 * Future me start here for time and space complexity.
 *
 * @param T The type of versioned component to be passed back to the callers [block].
 * @param R The return result of [block], allowing the caller to capture this defined
 * return result after utilizing the provided [T] and stripped [bytes].
 * @param bytes the payload to be processed - must hold its version at index 0.
 */
inline fun <T : Any, R> VersionRouter<T>.stripAndVersion(
    bytes: ByteArray,
    version: Byte,
    logger: Logger,
    block: (T, ByteArray) -> R
): R {
    if (bytes.isEmpty()) {
        logger.e { "Attempt to strip a version made against a null ByteArray." }
        throw MochaException.Persistent.CorruptionDetected("Empty payload received.")
    }

    val codec = getCodec(version)
    val strippedPayload = bytes.copyOfRange(1, bytes.size)

    return block(codec, strippedPayload)
}

/**
 * Version stripping and codec versioning through [Source.readByte].
 */
inline fun <T, R> stripAndVersion(
    source: Source,
    versionMap: Map<Byte, T>,
    logger: Logger,
    block: (T, Source) -> R
): R {
    if (source.exhausted()) {
        throw MochaException.Persistent.CorruptionDetected("Empty payload envelope received.")
    }

    val version = source.readByte()
    val codec = versionMap[version] ?: run {
        logger.e { "Unable to fetch codec. Unknown protocol version byte: $version" }
        throw MochaException.Persistent.UnknownProtocolVersion(version)
    }

    return block(codec, source)
}

