package com.mochame.utils

import kotlinx.io.Source

/**
 * Defines the contract for how the platform actuals should
 * manage the digestion of an incoming source, for hashing.
 */
interface Digest {
    fun update(source: Source)
    fun digest(): ByteArray
}

/**
 * SAM - call this interface and invoke an anonymous Digest object
 * per call. Thread safe and handles digest state carefully.
 */
fun interface Hasher {
    operator fun invoke(): Digest
}

/**
 * Chain the final digestion of the hashed byte array to a hex string.
 */
fun Digest.digestHex(): String = digest().toHexString()