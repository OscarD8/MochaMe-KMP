import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.insert-koin.compiler.plugin")
}

// ============================================================================
// TOML EXTRACTION & SCOPE ISOLATION
// ============================================================================
val catalogs: VersionCatalogsExtension = extensions.getByType(VersionCatalogsExtension::class.java)
val libs: VersionCatalog = catalogs.named("libs")

private fun getVersionAsString(alias: String): String {
    val constraint = libs.findVersion(alias)
        .orElseThrow { IllegalStateException("MochaMe Build Error: Missing version '$alias' in libs.versions.toml") }

    val versionString = constraint.requiredVersion.ifEmpty { constraint.preferredVersion }

    if (versionString.isEmpty()) {
        throw IllegalStateException("MochaMe Build Error: Version '$alias' exists but has no 'required' or 'preferred' value.")
    }
    return versionString
}

private fun getVersionAsInt(alias: String): Int {
    val versionString = getVersionAsString(alias)
    return versionString.toIntOrNull()
        ?: throw IllegalStateException("MochaMe Build Error: Version '$alias' ($versionString) cannot be parsed to an Integer.")
}

private fun getLibrary(alias: String) = libs.findLibrary(alias)
    .orElseThrow { IllegalStateException("MochaMe Build Error: Missing library '$alias' in libs.versions.toml") }

// ============================================================================
// TARGETS
// ============================================================================
kotlin {
    jvm()
    linuxX64()

    android {
        compileSdk = getVersionAsInt("android-compileSdk")
        minSdk = getVersionAsInt("android-minSdk")

        compilerOptions {
            val jvmVer = getVersionAsString("jvm")
            jvmTarget.set(JvmTarget.fromTarget(jvmVer))
        }

        withHostTestBuilder { }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    val osName = providers.systemProperty("os.name").get()
    val isMac = osName.contains("Mac OS X", ignoreCase = true)

    if (isMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

// ============================================================================
// TEST INJECTION
// ============================================================================
    sourceSets {
        val androidHostTest by getting {
            dependencies {
                implementation(getLibrary("test-robolectric"))
            }
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(getLibrary("androidx-test-core"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(getLibrary("test-junit-jupiter"))
            }
        }
    }
}

// ============================================================================
// DI & TEST LOGGING CONFIGURATION
// ============================================================================
koinCompiler {
    userLogs = true
    debugLogs = true
    unsafeDslChecks = true
    skipDefaultValues = true
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        showStandardStreams = false
        showExceptions = false
        events(TestLogEvent.FAILED)
    }
}