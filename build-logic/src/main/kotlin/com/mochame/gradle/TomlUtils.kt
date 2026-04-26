package com.mochame.gradle

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Provider

/**
 * Extension to safely extract versions as Strings, handling Rich Versions.
 */
fun VersionCatalog.getVersionAsString(alias: String): String {
    val constraint = findVersion(alias)
        .orElseThrow { IllegalStateException("MochaMe Build Error: Missing version '$alias' in libs.versions.toml") }

    val versionString = constraint.requiredVersion.ifEmpty { constraint.preferredVersion }

    if (versionString.isEmpty()) {
        throw IllegalStateException("MochaMe Build Error: Version '$alias' exists but has no 'required' or 'preferred' value.")
    }
    return versionString
}

/**
 * Extension to safely extract versions as Integers for SDK configurations.
 */
fun VersionCatalog.getVersionAsInt(alias: String): Int {
    val versionString = getVersionAsString(alias)
    return versionString.toIntOrNull()
        ?: throw IllegalStateException("MochaMe Build Error: Version '$alias' ($versionString) cannot be parsed to an Integer.")
}

/**
 * Extension to safely extract library providers.
 */
fun VersionCatalog.getLibrary(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias)
    .orElseThrow { IllegalStateException("MochaMe Build Error: Missing library '$alias' in libs.versions.toml") }