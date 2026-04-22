package com.mochame.app.infrastructure.utils

import co.touchlab.kermit.Logger
import kotlinx.io.Source
import kotlinx.io.readByteArray
import java.security.MessageDigest


actual fun createPlatformDigest(algorithm: String, logger: Logger): Digest =
    object : Digest {
        private val delegate = MessageDigest.getInstance(algorithm)
        private val log = logger.withTag("AndDigest-$algorithm")


        override fun update(source: Source) {
            val bytes = source.readByteArray()
            delegate.update(bytes)

            log.v { "Updated digest with ${bytes.size} bytes" }
        }

        override fun digest(): ByteArray {
            val result = delegate.digest()
            log.d { "Digest finalized | Hash Size: ${result.size} bytes" }
            return result
        }
    }