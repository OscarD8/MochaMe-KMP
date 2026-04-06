package com.mochame.app.infrastructure.utils

import java.security.MessageDigest


actual fun createPlatformDigest(algorithm: String): Digest = object : Digest {
    private val delegate = MessageDigest.getInstance(algorithm)

    override fun update(source: ByteArray) {
        delegate.update(source)
    }

    override fun digest(): ByteArray {
        return delegate.digest()
    }
}