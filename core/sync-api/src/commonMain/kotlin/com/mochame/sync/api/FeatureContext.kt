package com.mochame.sync.api

import com.mochame.sync.api.exceptions.MochaException

interface FeatureContext {
    val id: Int
    val featureName: String
    val modelName: String

    enum class Type(
        override val id: Int,
        override val featureName: String,
        override val modelName: String
    ) : FeatureContext {
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
        val allFeatureContext: List<FeatureContext> = Type.entries

        val allFeatureModules: List<String> by lazy {
            Type.entries
                .asSequence()
                .filter { it != Type.UNRECOGNIZED_FALLBACK }
                .map { it.featureName }
                .distinct()
                .toList()
        }

        fun fromString(model: String): FeatureContext {
            try {
                return Type.entries.first { it.modelName == model }
            } catch (e: NoSuchElementException) {
                throw MochaException.Persistent.CorruptionDetected("Error on Module Enum conversion from string. ${e.message}")
            }
        }
    }

}