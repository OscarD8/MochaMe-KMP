package com.mochame.app.domain.system.sync.utils

import kotlinx.serialization.Serializable

@Serializable
enum class MochaModule(val id: Int, val tag: String) {
    BIO(1, "bio"),
    RESONANCE(2, "resonance"),
    TELEMETRY(3, "telemetry");

    companion object {
        // Safe lookup for the Server Decoder
        fun fromId(id: Int) = entries.find { it.id == id }
    }
}