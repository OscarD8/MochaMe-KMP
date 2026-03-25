package com.mochame.app.data.repository.telemetry.bridge

import com.mochame.app.data.mappers.toDomain
import com.mochame.app.data.local.room.dao.TelemetryDao
import com.mochame.app.domain.telemetry.Domain
import com.mochame.app.domain.telemetry.Moment
import com.mochame.app.domain.telemetry.Space
import com.mochame.app.domain.telemetry.Topic
import com.mochame.app.domain.telemetry.repositories.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * ChronicleBridge: SQLite-backed implementation of [AnalyticsRepository].
 *
 * This bridge leverages [TelemetryDao] to execute high-performance historical
 * queries.
 *
 * To satisfy the "AI-Fundamentalism" directive, this bridge focuses on
 * lightweight stream mapping to prevent memory pressure during
 * background analysis by Gemini Nano.
 */
internal class AnalyticsBridge(
    private val dao: TelemetryDao
) : AnalyticsRepository {

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