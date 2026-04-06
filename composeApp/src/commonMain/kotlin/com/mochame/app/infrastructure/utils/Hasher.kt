package com.mochame.app.infrastructure.utils


interface Digest {
    fun update(source: ByteArray)
    fun digest(): ByteArray
}

expect fun createPlatformDigest(algorithm: String): Digest

fun interface Hasher {
    operator fun invoke(): Digest
}

fun sha256Hasher() = Hasher { createPlatformDigest("SHA-256") }
fun Digest.digestHex(): String = digest().toHexString()