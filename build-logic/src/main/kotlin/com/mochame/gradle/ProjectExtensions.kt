package com.mochame.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.koin.compiler.plugin.KoinGradleExtension

// ** Extension Functions **
/**
 * Standard utilities updated for the Koin Compiler Plugin (Post-KSP).
 * Handles configuration for the Koin Gradle extension, and
 * test logging for tasks.
 */
fun Project.standardConfigurations() {

    pluginManager.withPlugin("io.insert-koin.compiler.plugin") {
        mochaKoin {
            userLogs.set(true)
            debugLogs.set(true)
            unsafeDslChecks.set(true)
            skipDefaultValues.set(true)
        }
    }

    tasks.withType<AbstractTestTask>().configureEach {
        testLogging {
            showStandardStreams = false
            showExceptions = false
            events(TestLogEvent.FAILED)
        }
    }
}

/**
 * Accessor for the Koin Compiler.
 */
private fun Project.mochaKoin(configure: KoinGradleExtension.() -> Unit) {
    extensions.configure("koinCompiler", configure)
}


// ** Extension Properties **
/**
 * Accessor for the Version Catalog.
 */
val Project.libs: VersionCatalog
    get() = runCatching {
        extensions.getByType<VersionCatalogsExtension>().named("libs")
    }.getOrElse {
        error("\n[MochaMe Build Error]: The 'libs' Version Catalog is missing! \n" +
                "Ensure it is defined in your settings.gradle.kts.")
    }
/**
 * Accessor for OS detection.
 * Tracked by Gradle's Configuration Cache via 'project.providers'.
 */
val Project.isMac: Boolean
    get() = providers.systemProperty("os.name")
        .getOrElse("Unknown")
        .contains("Mac OS X", ignoreCase = true)