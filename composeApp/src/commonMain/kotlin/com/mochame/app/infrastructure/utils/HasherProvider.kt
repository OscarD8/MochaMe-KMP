package com.mochame.app.infrastructure.utils

import kotlinx.io.Source


interface Digest {
    fun update(source: Source)
    fun digest(): ByteArray
}

expect fun createPlatformDigest(algorithm: String): Digest

fun interface Hasher {
    operator fun invoke(): Digest
}

fun sha256Hasher() = Hasher { createPlatformDigest("SHA-256") }
fun Digest.digestHex(): String = digest().toHexString()