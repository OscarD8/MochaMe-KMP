package com.mochame.app.domain.repository.telemetry

import com.mochame.app.domain.model.telemetry.Domain
import com.mochame.app.domain.model.telemetry.Space
import com.mochame.app.domain.model.telemetry.Topic

/**
 * Defines the structural and organizational context of the system.
 *
 * This interface serves as the authoritative source for managing the "Map" of
 * the Mocha Me ecosystem: Domains, Topics, and Spaces. It is responsible for
 * maintaining the categorical hierarchy and preventing data fragmentation.
 *
 * All implementations must enforce "Organizational Integrity," ensuring that names
 * are unique within their scope (e.g., preventing duplicate Domains or
 * overlapping Topics within the same Domain).
 *
 * Architectural Role: Organizational Law & Structural Integrity.
 */
interface ContextRepository {

    // Domains
    suspend fun logDomain(name: String, hexColor: String = "#8D775F", iconKey: String, isActive: Boolean = true)
    suspend fun upsertDomain(domain: Domain)
    suspend fun deleteDomain(domainId: String)
    suspend fun archiveDomain(domainId: String)

    // Topics
    suspend fun logTopic(name: String, domainId: String, isActive: Boolean = true)
    suspend fun upsertTopic(topic: Topic)
    suspend fun deleteTopic(topicId: String)
    suspend fun archiveTopic(topicId: String)

    // Spaces
    suspend fun logSpace(name: String, iconKey: String, defaultBiophilia: Int?, isControlled: Boolean)
    suspend fun upsertSpace(space: Space)
    suspend fun deleteSpace(id: String)
    suspend fun archiveSpace(id: String)

}