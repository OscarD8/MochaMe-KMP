package com.mochame.app.domain.exceptions

/**
 * The single source of truth for all application failures.
 * Categorized by 'Actionability' to guide the Orchestrators and UI.
 */
sealed class MochaException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Transient failures: The system is healthy, but the request failed.
     * Action: Trigger the 'runWithRetry' loop with exponential backoff.
     */
    sealed class Transient(message: String, cause: Throwable? = null) :
        MochaException(message, cause) {
        class VaultBusy(cause: Throwable? = null) :
            Transient("The data vault is temporarily locked.", cause)

        class NetworkTimeout(cause: Throwable? = null) :
            Transient("The sync server took too long to respond.", cause)

        class Contention(message: String, cause: Throwable? = null) :
                Transient(message, cause)
    }

    /**
     * Persistent failures: The system has a fundamental issue.
     * Action: Stop execution, log a critical error, and alert the user.
     */
    sealed class Persistent(message: String, cause: Throwable? = null) :
        MochaException(message, cause) {
        class VaultFatal(message: String, cause: Throwable? = null) : Persistent(message, cause)
        class DiskFull(cause: Throwable) :
            Persistent("Cannot write to disk; storage is full.", cause)

        class CorruptionDetected(message: String) : Persistent(message)

        class BootTimeout(message: String) : Persistent(message)

        class ClockSkew(
            val driftDisplay: Long,
            cause: Throwable? = null
        ) : Persistent("System clock is out of sync by $driftDisplay days", cause)

        class SyncInitializationException(
            message: String,
            cause: Throwable? = null
        ) : Exception(message, cause)

        class HlcParseException(rawString: String) :
            RuntimeException("Failed to parse HLC string: '$rawString'. Data integrity at risk.")
    }

    /**
     * Policy failures: The request violated domain rules.
     * Action: Resolve the conflict (e.g., Client Wins / Server Wins).
     */
    sealed class Policy(message: String, cause: Throwable? = null) :
        MochaException(message, cause) {
        class CausalityViolation(message: String) : Policy(message)
        class IdentityConflict(message: String) : Policy(message)
    }

    /**
     * Semantic Exceptions: These represent violations of business rules
     * or domain constraints. Unlike System errors, these are "Expected"
     * outcomes of user actions or sync conflicts.
     */
    sealed class SemanticException(
        message: String,
        cause: Throwable? = null
    ) : MochaException(message, cause) {

        // SPACES
        sealed class Space(message: String) : SemanticException(message) {
            data class NotFound(val id: String) : Space("Space $id was not found.")
            data class AlreadyExists(val name: String) : Space("Space '$name' already exists.")
            data class InUse(val id: String, val count: Int) : Space(
                "Cannot delete space $id: $count moments are still anchored here."
            )
        }


        // ===================== RESONANCE ========================

        // AUTHORS
        sealed class Author(message: String) : SemanticException(message) {
            data class NotFound(val id: String) : Author("Author $id not found.")
            data class InUse(val id: String, val bookCount: Int) : Author(
                "Cannot delete author $id: It still contains $bookCount books."
            )
        }

        // BOOKS
        sealed class Book(message: String) : SemanticException(message) {
            data class NotFound(val id: String) : Book("Book $id not found.")

            data class InUse(val id: String, val quoteCount: Int) : Book(
                "Cannot delete book $id: It still has $quoteCount associated quotes."
            )
        }

        // ===================== TELEMETRY ========================

        // DOMAINS
        sealed class Domain(message: String) : SemanticException(message) {
            data class InUse(val id: String, val momentCount: Int) : Book(
                "Cannot delete Domain $id: It still has $momentCount associated quotes."
            )

            data class AlreadyExists(val name: String, val domainId: String) :
                Domain("Domain '$name' already exists in domain $domainId.")


            data class NotFound(val id: String) : Domain("Domain $id not found.")
        }

        // TOPICS & CATEGORIES
        sealed class Topic(message: String) : SemanticException(message) {
            data class AlreadyExists(val name: String, val topicId: String) :
                Topic("Topic '$name' already exists in domain $topicId.")

            data class NotFound(val id: String) : Topic("Topic $id not found.")

            data class InUse(val id: String, val momentCount: Int) : Book(
                "Cannot delete Topic $id: It still has $momentCount associated quotes."
            )
        }
    }
}