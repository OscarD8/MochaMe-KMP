package com.mochame.telemetry.infrastructure.bridge


import com.mochame.contract.di.IoContext
import com.mochame.telemetry.data.TelemetryDao
import com.mochame.telemetry.data.toDomain
import com.mochame.telemetry.domain.Domain
import com.mochame.telemetry.domain.Moment
import com.mochame.telemetry.domain.Space
import com.mochame.telemetry.domain.Topic
import com.mochame.telemetry.domain.repositories.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * ChronicleBridge: SQLite-backed implementation of [com.mochame.telemetry.domain.repositories.AnalyticsRepository].
 *
 * This bridge leverages [com.mochame.telemetry.data.TelemetryDao] to execute high-performance historical
 * queries.
 *
 * To satisfy the "AI-Fundamentalism" directive, this bridge focuses on
 * lightweight stream mapping to prevent memory pressure during
 * background analysis by Gemini Nano.
 */
internal class AnalyticsBridge(
    private val dao: TelemetryDao,
    @IoContext private val ioContext: CoroutineContext
) : AnalyticsRepository {
    // NEED TO IMPLEMENT DISPATCHER

    // --- DOMAINS ---
    override fun getActiveDomains(): Flow<List<Domain>> {
        return dao.observeActiveDomains().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getInactiveDomains(): Flow<List<Domain>> {
        return dao.observeInactiveDomains().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDomainByIdFlow(id: String): Flow<Domain?> {
        return dao.observeDomainById(id).map { it?.toDomain() }
    }

    // --- TOPICS ---

    override fun getTopic(topicId: String): Flow<Topic?> {
        return dao.observeTopicById(topicId).map { it?.toDomain() }
    }

    override fun getAllTopicsByDomain(domainId: String): Flow<List<Topic>> {
        return dao.observeActiveTopicsInDomain(domainId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // --- MOMENTS ---
    override fun getMomentsByDay(epochDay: Long): Flow<List<Moment>> {
        return dao.observeMomentsByEpochDay(epochDay).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // --- SPACES ---
    override fun getActiveSpaces(): Flow<List<Space>> =
        dao.observeActiveSpaces()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged() // Prevents unnecessary UI ripples

    override suspend fun getSpaceById(id: String): Space? =
        dao.getSpaceById(id)?.toDomain()
}