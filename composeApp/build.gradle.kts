import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.mochame.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room Compiler targets for all platforms
    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
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