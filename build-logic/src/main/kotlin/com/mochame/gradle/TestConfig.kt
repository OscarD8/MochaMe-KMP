package com.mochame.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.api.artifacts.VersionCatalog

/**
 * Lifecycle-safe testing environment provider using lazy providers.
 */
fun KotlinMultiplatformExtension.configureTestTargets(libs: VersionCatalog) {
    sourceSets.apply {
        // Lazy Capture
        val commonTestProvider = named("commonTest")

        // Lazy Configuration
        commonTestProvider.configure {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.getLibrary("kotlinx-coroutines-test"))
                implementation(libs.getLibrary("turbine"))
                implementation(libs.getLibrary("koin-test"))
                implementation(libs.getLibrary("kermit-test"))

                implementation(project.project(":core:test:support"))
            }
        }

        named("androidHostTest") {
            dependsOn(commonTestProvider.get())
            dependencies {
                implementation(libs.getLibrary("junit4"))
                implementation(libs.getLibrary("test-robolectric"))
                implementation(libs.getLibrary("test-mockk"))
                runtimeOnly(libs.getLibrary("junit-vintage-engine"))
                implementation(libs.getLibrary("androidx-test-core"))
            }
        }

        named("jvmTest") {
            // Note: CommonTest link is handled implicitly by KGP for JVM
            dependencies {
                implementation(libs.getLibrary("test-junit-jupiter"))
            }
        }

        named("linuxX64Test") {
            dependsOn(commonTestProvider.get())
        }

        named("androidDeviceTest") {
            dependsOn(commonTestProvider.get())
            dependencies {
                implementation(libs.getLibrary("junit4"))
                implementation(libs.getLibrary("androidx-test-ext-junit"))
                implementation(libs.getLibrary("androidx-runner"))
                implementation(libs.getLibrary("androidx-test-core"))
                implementation(libs.getLibrary("koin-android"))
            }
        }
    }
}
