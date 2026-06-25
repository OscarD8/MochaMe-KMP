package com.mochame.sync.contract

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import kotlinx.io.Source

/**
 * Basic contract for all version control functionality.
 *
 * @param T Type of object assigned to a version on the [versionMap]
 * @property latestVersion used on [latestCodec] as the key.
 * @property versionMap maps a byte value representing a version to whatever Type is provided.
 */
interface VersionRouter<T : Any> {
    val latestVersion: Byte
    // Know that this is creating overhead by boxing the Byte type
    val versionMap: Map<Byte, T>
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
 */
val <T : Any> VersionRouter<T>.latestCodec: T
    get() = versionMap[latestVersion] ?: throw MochaException.Persistent.CorruptionDetected(
        "No component registered for version $latestVersion"
    )


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
 * Strips the version from [bytes] index 0, utilizing [versionMap] to provide [block]
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
inline fun <T, R> stripAndVersion(
    bytes: ByteArray,
    versionMap: Map<Byte, T>,
    logger: Logger,
    block: (T, ByteArray) -> R
): R {
    if (bytes.isEmpty()) {
        throw MochaException.Persistent.CorruptionDetected("Empty payload envelope received.")
    }

    val version = bytes[0]
    val codec = versionMap[version] ?: run {
        logger.e { "Unable to fetch codec. Unknown protocol version byte: $version" }
        throw MochaException.Persistent.UnknownProtocolVersion(version)
    }

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

