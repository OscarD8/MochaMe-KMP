import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.insert-koin.compiler.plugin")
}

// ============================================================================
// TOML EXTRACTION & SCOPE ISOLATION
// ============================================================================
val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)
val libs = catalogs.named("libs")

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
}

koinCompiler {
    userLogs = true
    debugLogs = true
    unsafeDslChecks = true
    skipDefaultValues = true
}