package com.mochame.orchestrator


enum class MochaModule(val id: Int, val tag: String) {
    BIO(1, "bio"),
    RESONANCE(2, "resonance"),
    TELEMETRY(3, "telemetry");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id }
    }
}