package com.mochame.app.domain.repository.telemetry

import com.mochame.app.domain.model.telemetry.Domain
import com.mochame.app.domain.model.telemetry.Moment
import com.mochame.app.domain.model.telemetry.Space
import com.mochame.app.domain.model.telemetry.Topic
import kotlinx.coroutines.flow.Flow

/**
 * Defines the analytical retrieval and aggregation layer for historical telemetry.
 *
 * This interface serves as the primary provider of "Levers"—summarized,
 * context-rich datasets designed for localized analysis by Gemini Nano (unsure on JVM)
 * and historical visualization in the UI.
 * * All data returned via the Chronicle must respect the "Cup vs. Brew"
 * relationship, ensuring that moments are always presented within their
 * correct biological context (associatedEpochDay).
 *
 * Architectural Role: Analytical Engine & History Provider.
 */
interface ChronicleActions {

    // DOMAIN
    fun getActiveDomains(): Flow<List<Domain>>
    fun getInactiveDomains(): Flow<List<Domain>>
    fun getDomainByIdFlow(id: String): Flow<Domain?>

    // TOPIC
    fun getTopic(topicId: String): Flow<Topic?>
    fun getAllTopicsByDomain(domainId: String): Flow<List<Topic>>

    // MOMENT
    fun getMomentsByDay(epochDay: Long): Flow<List<Moment>>

    // SPACE
    fun getActiveSpaces(): Flow<List<Space>>
    suspend fun getSpaceById(id: String): Space?

}