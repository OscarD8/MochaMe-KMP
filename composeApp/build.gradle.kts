@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {

    android {
        namespace = "com.mocha.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // This command "unlocks" the folder you are looking for
        withHostTestBuilder {  }

        // The "Device Test" (Hardware Realism)
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        // Enable resources for Robolectric/Compose tests
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        // 1. SHARED BRAIN: Code here runs on Android AND Linux
        commonMain.dependencies {
            // UI Framework
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.windowSizeClass)
            implementation(libs.compose.adaptive.nav.suite)
            implementation(libs.adaptive.navigation3)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)
            implementation(libs.material.icons.extended)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.uuid)
            // Mocha Me Logic & Data
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled) // Essential for Linux DB
            // Core Koin
            implementation(libs.koin.core)
            // The DSL for 'viewModel { ... }'
            implementation(libs.koin.compose.viewmodel)
            // For 'koinViewModel()' inside @Composable
            implementation(libs.koin.compose)
        }

        // 2. ANDROID SPECIFICS: Only for the phone
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.android)
            implementation(libs.work.runtime.ktx)
        }

        // 3. LINUX SPECIFICS: Only for the desktop
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing) // Prevents crashes on Linux UI
        }

        jvmTest.dependencies {
            implementation(libs.test.junit.jupiter)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Essential for runTest and coroutine control
            implementation(libs.kotlinx.coroutines.test)
            // Highly recommended for testing StateFlows/Flows from your DAOs
            implementation(libs.turbine)
            implementation(libs.koin.test)
        }

        val androidHostTest by getting {
            dependencies {
                // 1. THE ENGINE
                implementation(libs.junit4)  // Provides @RunWith and org.junit.Test
                // 2. THE SHADOW (Robolectric)
                implementation(libs.test.robolectric)
                // 3. THE MOCKS
                implementation(libs.test.mockk)
                // Allows test context
                implementation(libs.androidx.core)

                // necessary as something like the junit runner is not working without it for robolectric
                runtimeOnly(libs.junit.vintage.engine)
            }
        }

        val androidDeviceTest by getting {
            dependencies {
                // THE BRAIN (Must match Host for code compatibility)
                implementation(libs.junit4)
                // These replace the need for vintageEngine on device tests
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.runner)
                // Note: Engine is provided by the Android-KMP plugin's test runner

                // THE REAL BODY (No Robolectric)
                implementation(libs.androidx.core)
                implementation(libs.test.mockk)

                // THE NERVOUS SYSTEM (Must match Host)
                implementation(libs.koin.android)
                implementation(libs.koin.test)
            }
        }

    }
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlinx.datetime.ExperimentalKotlinxDateTimeApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)

    // NEW: Ensure Room generates the _Impl classes for your Robolectric tests
    add("kspAndroidHostTest", libs.room.compiler)

    add("kspJvm", libs.room.compiler)

    // iOS targets
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64Test", libs.room.compiler)
}

compose.desktop {
    application {
        // If your package is 'com.mochame.app', use that prefix.
        mainClass = "com.mochame.app.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "MochaMe"
            packageVersion = "1.0.0"
        }
    }
}