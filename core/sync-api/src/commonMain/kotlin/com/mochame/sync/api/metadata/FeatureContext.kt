package com.mochame.sync.api.metadata

import com.mochame.sync.api.exceptions.MochaException
import kotlinx.serialization.Serializable

@Serializable
enum class FeatureContext(
    val moduleId: Int,
    val featureName: String,
    val modelName: String
) {
    UNRECOGNIZED_MODEL(0, "UNRECOGNIZED", "MODEL"),

    BIO_DAILY_CONTEXT(1, "BIO", "DAILY_CONTEXT"),

    TELEMETRY_TOPIC(2, "TELEMETRY", "TOPIC"),
    TELEMETRY_DOMAIN(2, "TELEMETRY", "DOMAIN"),
    TELEMETRY_MOMENT(2, "TELEMETRY", "MOMENT"),

    RESONANCE_BOOK(3, "RESONANCE", "BOOK"),
    RESONANCE_AUTHOR(3, "RESONANCE", "AUTHOR"),
    RESONANCE_QUOTE(3, "RESONANCE", "QUOTE");

    companion object {
        val allFeatureModules: List<String> by lazy {
            entries
                .asSequence()
                .filter { it != UNRECOGNIZED_MODEL }
                .map { it.featureName }
                .distinct()
                .toList()
        }

        private val modelLookup by lazy {
            entries.associateBy { it.modelName }
        }

        fun fromModelString(model: String): FeatureContext {
            return modelLookup[model]
                ?: throw MochaException.Persistent.CorruptionDetected("Unknown model name: $model")
        }
    }
}