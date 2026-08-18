package com.mochame.sync.api.metadata

import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.spi.metadata.InternalTestApi
import kotlinx.serialization.Serializable


// -----------------------------------------------------------
// IMPLEMENTATION
// -----------------------------------------------------------
@Serializable
enum class FeatureContext(
    val modelId: Int,
    val featureName: String,
    val modelName: String
) {
    UNRECOGNIZED_MODEL(0, "SYSTEM", "UNRECOGNIZED"),

    BIO_DAILY_CONTEXT(1, "BIO", "DAILY_CONTEXT"),

    TELEMETRY_TOPIC(2, "TELEMETRY", "TOPIC"),
    TELEMETRY_DOMAIN(3, "TELEMETRY", "DOMAIN"),
    TELEMETRY_MOMENT(4, "TELEMETRY", "MOMENT"),

    RESONANCE_BOOK(5, "RESONANCE", "BOOK"),
    RESONANCE_AUTHOR(6, "RESONANCE", "AUTHOR"),
    RESONANCE_QUOTE(7, "RESONANCE", "QUOTE"),

    @InternalTestApi
    TEST_STUB_A(9001, "TEST", "A"),
    @InternalTestApi
    TEST_STUB_B(9002, "TEST", "B");


    companion object {
        val allFeatureModules: List<String> by lazy {
            entries
                .asSequence()
                .filter { it != UNRECOGNIZED_MODEL }
                .map { it.featureName }
                .distinct()
                .toList()
        }

        private val modelStringLookup by lazy { entries.associateBy { it.modelName } }

        fun fromModelString(model: String) = modelStringLookup[model]
            ?: throw MochaException.Persistent.CorruptionDetected("Unknown model name: $model")

        private val idLookup: Map<Int, FeatureContext> = buildMap(entries.size) {
            for (entry in FeatureContext.entries) {
                val existing = put(entry.modelId, entry)
                require(existing == null) {
                    "Duplicate moduleId detected: ${entry.modelId} on ${entry.name} and ${existing?.name}"
                }
            }
        }

        fun fromModelId(id: Int): FeatureContext = idLookup[id] ?: UNRECOGNIZED_MODEL
    }
}