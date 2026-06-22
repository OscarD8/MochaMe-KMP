package com.mochame.contract.metadata

import com.mochame.contract.exceptions.MochaException

interface MochaModuleContext {
    val id: Int
    val moduleName: String
    val modelName: String

    enum class Type(
        override val id: Int,
        override val moduleName: String,
        override val modelName: String
    ) : MochaModuleContext {
        BIO_DAILY_CONTEXT(1, "BIO", "DAILYCONTEXT"),

        TELEMETRY_TOPIC(2, "TELEMETRY", "TOPIC"),
        TELEMETRY_DOMAIN(2, "TELEMETRY", "DOMAIN"),
        TELEMETRY_MOMENT(2, "TELEMETRY", "MOMENT"),

        RESONANCE_BOOK(3, "RESONANCE", "BOOK"),
        RESONANCE_AUTHOR(3, "RESONANCE", "AUTHOR"),
        RESONANCE_QUOTE(3, "RESONANCE", "QUOTE"),

        // Used for testing or as fall back
        UNRECOGNIZED_FALLBACK(0, "UNKNOWN", "UNKNOWN");
    }

    companion object {
        val allMochaModuleContext: List<MochaModuleContext> = Type.entries

        val allModules: List<String> = Type.entries.map { it.moduleName }.distinct()

        fun contextFromString(model: String): MochaModuleContext {
            try {
                return Type.entries.first { it.modelName == model }
            } catch (e: NoSuchElementException) {
                throw MochaException.Persistent.CorruptionDetected("Error on Module Enum conversion from string. ${e.message}")
            }
        }
    }

}