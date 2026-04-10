package com.mochame.app.infrastructure.utils

import co.touchlab.kermit.Logger
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.infrastructure.native.openssl.EVP_DigestFinal_ex
import com.mochame.app.infrastructure.native.openssl.EVP_DigestInit_ex
import com.mochame.app.infrastructure.native.openssl.EVP_DigestUpdate
import com.mochame.app.infrastructure.native.openssl.EVP_MD_CTX_free
import com.mochame.app.infrastructure.native.openssl.EVP_MD_CTX_new
import com.mochame.app.infrastructure.native.openssl.EVP_MD_size
import com.mochame.app.infrastructure.native.openssl.EVP_get_digestbyname
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.io.Source

actual fun createPlatformDigest(algorithm: String, logger: Logger): Digest {
    return LinuxOpenSSLDigest(algorithm, logger)
}

@OptIn(ExperimentalForeignApi::class)
class LinuxOpenSSLDigest(
    private val algorithm: String,
    logger: Logger
) : Digest, AutoCloseable {

    private val log = logger.withTag("OpenSSL-${algorithm}")

    private val ctx = EVP_MD_CTX_new()
        ?: throw MochaException.Persistent.Internal("OpenSSL: Context init failed")
    private val md = EVP_get_digestbyname(algorithm)
        ?: throw MochaException.Persistent.Internal("OpenSSL: Invalid Algo")

    init {
        EVP_DigestInit_ex(ctx, md, null)
        log.v { "Native context initialized for $algorithm" }
    }

    /**
     * Efficiently updates the digest using zero-copy direct memory access.
     */
    override fun update(source: Source) {
        val tempBuffer = ByteArray(8192)

        while (true) {
            val read = source.readAtMostTo(tempBuffer)
            if (read <= 0L) break

            tempBuffer.usePinned { pinned ->
                EVP_DigestUpdate(ctx, pinned.addressOf(0), read.toULong())
            }
        }
        // Verbose logging to avoid polluting production logs during high-frequency IO
        log.v { "Hashed chunk successfully" }
    }

    override fun digest(): ByteArray {
        val hashSize = EVP_MD_size?.invoke(md)
            ?: throw MochaException.Persistent.Internal("OpenSSL: Could not resolve EVP_MD_size function pointer")
        val hash = ByteArray(hashSize)

        hash.usePinned { pinned ->
            EVP_DigestFinal_ex(ctx, pinned.addressOf(0).reinterpret(), null)
        }

        log.d { "Digest finalized | Size: $hashSize bytes" }
        return hash
    }

    override fun close() {
        EVP_MD_CTX_free(ctx)
        log.v { "Native heap cleared: EVP_MD_CTX freed" }
    }
}