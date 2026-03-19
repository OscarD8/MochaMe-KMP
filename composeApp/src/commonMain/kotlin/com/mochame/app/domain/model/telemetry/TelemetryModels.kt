package com.mochame.app.domain.model.telemetry

import com.mochame.app.core.SyncStatus

/**
 * A specific moment of logging.
 */
data class MomentDraft(
    val domainId: String,
    val topicId: String?,
    val spaceId: String?,
    val core: MomentCore,
    val detail: MomentDetail
)

data class Moment(
    val id: String,
    val domainId: String,
    val topicId: String?,
    val spaceId: String?,
    val core: MomentCore,
    val detail: MomentDetail,
    val context: MomentClimate,
    val metadata: MomentMetadata
)

// 1. The Core (Required User Input)
data class MomentCore(
    val satisfactionScore: Int,
    val mood: Mood,
    val energyDelta: Int,
    val intensityScale: Int,
)

// 2. The subjective "Extra" - Optional from User
data class MomentDetail(
    val note: String? = null,
    val isFocusTime: Boolean? = null,
    val socialScale: Int? = null,
    val entryEnergy: Int? = null,
    val biophiliaScale: Int? = null,
    val durationMinutes: Int? = null
)

// 3. Automated by System
data class MomentClimate(
    val isDaylight: Boolean? = null,
    val cloudDensity: Int? = null,
    val isPrecipitating: Boolean? = null
)

// 4. The system "Audit" - Managed by Repository
data class MomentMetadata(
    val timestamp: Long,
    val associatedEpochDay: Long,
    val lastModified: Long,
    val syncStatus: Int = SyncStatus.PENDING.value // New: State-Aware Flag
)


data class Domain(
    val id: String,
    val name: String,
    val hexColor: String,
    val iconKey: String, // Maps to a UI resource in the View layer
    val isActive: Boolean = true,
    val lastModified: Long
)


data class Topic(
    val id: String,
    val domainId: String,
    val name: String,
    val isActive: Boolean,
    val lastModified: Long
)


data class Space(
    val id: String,
    val name: String,
    val iconKey: String,
    val defaultBiophilia: Int?,
    val isControlled: Boolean,
    val isActive: Boolean,
    val lastModified: Long
)

/**
 * PAD (Pleasure, Energy, Agency) Emotional State Model.
 * Scaled -2 to +2 for centering in AI analysis.
 */
enum class Mood(
    val pleasure: Int,   // Valence/Pleasure
    val energy: Int,    // Activation/Energy
    val agency: Int   // Agency/Control
) {
    FOCUS(1, 2, 2),
    WONDER(2, 1, 1),
    ENERGIZED(1, 2, 1),
    CALM(1, -1, 2),
    NEUTRAL(0, 0, 0),
    BORED(-1, -2, 0),
    TIRED(-1, -2, -1),
    SAD(-2, -1, -2),
    FRUSTRATED(-2, 2, -1);

    companion object {
        fun fromName(name: String?): Mood = entries.find { it.name == name } ?: NEUTRAL
    }
}