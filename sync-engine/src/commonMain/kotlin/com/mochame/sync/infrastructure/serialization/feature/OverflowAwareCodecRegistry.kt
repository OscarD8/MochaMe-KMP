package com.mochame.sync.infrastructure.serialization.feature

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.sync.contract.LocalFirstEntity
import com.mochame.sync.domain.components.FeatureCodecRegistry
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.infrastructure.serialization.CodecRegistry

/**
 * Base class for feature registries to extend.
 * Handles the routing of overflow blob id's via an intercepting
 * decode method, routing to the standard registries decode method if no
 * [com.mochame.sync.data.entities.SyncIntentEntity.overflowBlobId].
 * If the intent came in with an overflow, a Transient
 * error is currently thrown, awaiting further development.
 *
 * Class is expected to be called by the SyncReceiver.
 */
abstract class OverflowAwareCodecRegistry<T : LocalFirstEntity<T>, TCodec : FeatureCodec<T>>(
    logger: Logger,
    codecMap: Map<Byte, TCodec>,
    latestVersion: Byte
) : CodecRegistry<TCodec>(codecMap, logger, latestVersion),
    FeatureCodecRegistry<T> {

    /**
     * Overflow-aware decode. Called by the coordinator via processRemoteIntent.
     * If payload is null and blobId is set, logs the overflow path and throws
     * a typed exception — the actual blob fetch is a future concern.
     * If payload is present, delegates to the concrete registry's version-routing decode.
     */
    override fun decode(data: ByteArray?, blobId: String?, context: DecodeContext): T {
        return when {
            data != null -> {
                decode(data, context)
            }

            blobId != null -> {
                logger.w {
                    "Overflow payload detected for ${context.id} | blobId: $blobId | Blob resolution not yet implemented."
                }
                throw MochaException.Transient.BlobResolutionPending(blobId)
            }

            else -> {
                logger.e { "Received null payload with no overflow reference for ${context.id}" }
                throw MochaException.Persistent.CorruptionDetected("Null payload with no blobId for ${context.id}")
            }
        }
    }

}