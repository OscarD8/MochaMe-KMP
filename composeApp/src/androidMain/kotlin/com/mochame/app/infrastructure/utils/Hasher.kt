package com.mochame.app.infrastructure.utils

import kotlinx.io.Source
import kotlinx.io.readByteArray
import java.security.MessageDigest


actual fun createPlatformDigest(algorithm: String): Digest = object : Digest {
    private val delegate = MessageDigest.getInstance(algorithm)

    override fun update(source: Source) {
        delegate.update(source.readByteArray())
    }

    override fun digest(): ByteArray {
        return delegate.digest()
    }
}