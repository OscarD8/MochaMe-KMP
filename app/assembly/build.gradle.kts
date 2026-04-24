import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    android {
        namespace = "com.mochame.app.assembly"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
        }
    }

    jvm()
    linuxX64()

    val isMac = System.getProperty("os.name") == "Mac OS X"
    if (isMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sync-engine"))
            implementation(project(":core:orchestrator"))
            implementation(project(":core:platform"))
            implementation(project(":core:metadata"))
            implementation(project(":core:utils"))
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":core:test:support"))
            implementation(project(":core:test:orchestrator-test"))
        }
    }

    koinCompiler {
        userLogs = true           // Log component detection
        debugLogs = false          // Log internal processing (verbose)
        unsafeDslChecks =
            true    // Validates create() is the only instruction in lambda (default: true)
        skipDefaultValues =
            true  // Skip injection for parameters with default values (default: true)
        compileSafety = true
    }

}