package com.mochame.app.domain.exceptions

/**
 * Base for all domain-level failures.
 * Ensures we don't leak Infrastructure (SQLite) types to the UI.
 */
sealed class DomainException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when the local-first storage (The Vault) is inaccessible
 * due to terminal contention or hardware failure.
 */
class VaultUnavailableException(
    message: String = "The data vault is currently busy or inaccessible. Please try again.",
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * Thrown when a write violates causality (e.g., HLC drift).
 */
class CausalityViolationException(
    message: String,
    cause: Throwable? = null
) : DomainException(message, cause)