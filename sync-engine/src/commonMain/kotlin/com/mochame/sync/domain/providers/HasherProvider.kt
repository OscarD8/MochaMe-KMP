package com.mochame.sync.domain.providers

import kotlinx.io.Source


interface Digest {
    fun update(source: Source)
    fun digest(): ByteArray
}

fun interface Hasher {
    operator fun invoke(): Digest
}

fun Digest.digestHex(): String = digest().toHexString()