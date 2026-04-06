package com.mochame.app.domain.feature.telemetry

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
    val metadata: MomentMetadata,
    val attachments: List<MomentAttachment>
)

data class MomentAttachment(
    val id: String,
    val contentBlobId: String, // The "Ticket" to the BlobStore
    val mimeType: String,      // Tells the UI how to render (Video/Photo/etc)
    val fileName: String,
    val fileSize: Long,
    val localUri: String?      // Only non-null if the file is physically on THIS device
)

/*
class RoomMomentRepository(
    private val momentDao: MomentDao,
    private val attachmentDao: AttachmentDao,
    private val codec: MomentPayloadCodecRegistry
) : MomentRepository {

    override fun getMoment(id: String): Flow<Moment?> {
        // We use Room's ability to observe multiple tables
        return combine(
            momentDao.getMomentById(id),
            attachmentDao.getAttachmentsForMoment(id)
        ) { entity, attachmentEntities ->
            if (entity == null) return@combine null

            // Map the flat entities into the rich Aggregate Root
            entity.toDomain(attachmentEntities)
        }
    }
}
 */

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

// HLC PLEASE
// 4. The system "Audit" - Managed by Repository
data class MomentMetadata(
    val timestamp: Long,
    val associatedEpochDay: Long,
    val lastModified: Long,
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