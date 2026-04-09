package com.mochame.app.infrastructure.utils

import kotlinx.io.Source
import kotlinx.io.readByteArray
import java.security.MessageDigest

actual fun createPlatformDigest(algorithm: String): Digest = object : Digest {
    private val delegate = MessageDigest.getInstance(algorithm)

    /**
     * Reads the source directly into the algorithm as a byteArray.
     * There is no check on the size of the byteArray being read so it assumes
     * a value that is already being chunked.
     */
    override fun update(source: Source) {
        delegate.update(source.readByteArray())
    }

    override fun digest(): ByteArray {
        return delegate.digest()
    }
}