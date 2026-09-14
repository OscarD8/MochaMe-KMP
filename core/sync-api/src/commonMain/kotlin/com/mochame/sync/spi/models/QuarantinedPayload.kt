package com.mochame.sync.spi.models

data class QuarantinedPayload (
    val watermark: Long,
    val rawPayload: ByteArray,
    val failureReason: String,
    val receivedAt: Long
)