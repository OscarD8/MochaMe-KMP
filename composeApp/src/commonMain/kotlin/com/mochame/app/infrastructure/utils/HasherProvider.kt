package com.mochame.app.infrastructure.utils

import co.touchlab.kermit.Logger
import kotlinx.io.Source


interface Digest {
    fun update(source: Source)
    fun digest(): ByteArray
}

expect fun createPlatformDigest(algorithm: String, logger: Logger): Digest

fun interface Hasher {
    operator fun invoke(): Digest
}

fun Digest.digestHex(): String = digest().toHexString()