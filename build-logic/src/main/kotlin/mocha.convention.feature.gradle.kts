import com.mochame.gradle.applyStandardDependencies
import com.mochame.gradle.configureTargets
import com.mochame.gradle.getLibrary
import com.mochame.gradle.libs
import com.mochame.gradle.standardConfigurations

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.insert-koin.compiler.plugin")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

standardConfigurations()

kotlin {
    configureTargets(
        project = this@Project,
        libs = libs,
        includeTestBuilders = true
    )

    applyStandardDependencies(this@Project)

    sourceSets {
        val commonMainProvider = named("commonMain")
        commonMainProvider.configure {
            dependencies {
                implementation(libs.getLibrary("room-runtime"))
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val roomCompiler = libs.getLibrary("room-compiler")

    kotlin.targets.configureEach {
        if (this !is org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget) {
            val targetName = name.replaceFirstChar { it.uppercase() }

            // 1. Unified Naming Strategy for 2026
            val kspNames = if (name == "android") {
                // Updated for Gradle 9.1+ / AGP 8.8+ Unified Naming
                listOf("kspAndroidHostTest", "kspAndroidDeviceTest")
            } else {
                // Standard KMP (JVM, Linux, iOS)
                listOf("ksp${targetName}Test")
            }

            // 2. Surgical Injection
            kspNames.forEach { kspName ->
                configurations.findByName(kspName)?.let { config ->
                    project.dependencies.add(config.name, roomCompiler)
                }
            }
        }
    }
}

