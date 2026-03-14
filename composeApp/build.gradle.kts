@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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


// Keep specialized JUnit settings for the Host/JVM tasks
tasks.withType<Test>().configureEach {
    if (name.contains("jvm", ignoreCase = true)) {
        useJUnitPlatform()
    } else if (name.contains("Host", ignoreCase = true)) {
        useJUnit()
    }
    testLogging {
        showStandardStreams = true
    }
}

kotlin {
    android {
        namespace = "com.mocha.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // This command "unlocks" the folder you are looking for
        withHostTestBuilder { }

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

    val isMac = System.getProperty("os.name") == "Mac OS X"

    if (isMac) {
        // 1. Define the targets
        val iosTargets = listOf(
            iosArm64(),
            iosSimulatorArm64()
        )

        // 2. Configure the targets
        iosTargets.forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
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
            // This is the specific string Kotlin uses to bridge to JUnit 5
            implementation(kotlin("test-junit5"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // Concurrency
            implementation(libs.kotlinx.coroutines.test)
            // Highly recommended for testing StateFlows/Flows from your DAOs
            implementation(libs.turbine)
            // Koin
            implementation(libs.koin.test)
            implementation(libs.koin.core)
        }

        val androidHostTest by getting {
            dependencies {
                // The Core Framework (JUnit 4)
                implementation(libs.junit4)
                // The Environment (Robolectric)
                implementation(libs.test.robolectric)
                // The Mocking (MockK)
                implementation(libs.test.mockk)
                // The Runner Adapter (Essential for Gradle to see JUnit 4 tests in 2026)
                runtimeOnly(libs.junit.vintage.engine)
                // Android Context Support (Robolectric needs this for Activity/Context tests)
                implementation(libs.androidx.test.core)
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
                implementation(libs.androidx.test.core)
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
    val isMac = System.getProperty("os.name") == "Mac OS X"

    add("kspCommonMainMetadata", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    // Ensure Room generates the _Impl classes for the Robolectric tests
    add("kspAndroidHostTest", libs.room.compiler)
    add("kspJvm", libs.room.compiler)

    // iOS targets
    if (isMac) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
        add("kspIosSimulatorArm64Test", libs.room.compiler)
    }

}

compose.desktop {
    application {
        mainClass = "com.mochame.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "MochaMe"
            packageVersion = "1.0.0"
        }
    }
}

// ======================== Lint KSP Race Conditions? ===========================

// 1. Fix for KSP/Lint racing conditions
// This ensures the compiler doesn't have to 'infer' the type from the filter?
val kspTasks: TaskCollection<Task> = tasks.matching {
    it.name.startsWith("ksp")
}

val lintTasks: TaskCollection<Task> = tasks.matching {
    it.name.contains("Lint", ignoreCase = true)
}

// 2. Use configureEach to lazily apply the ordering
lintTasks.configureEach {
    // 'this' is now explicitly a Task within the collection
    mustRunAfter(kspTasks)
}


// =========================== TESTING FORMATTING ===========================
val targetTaskNames = setOf("jvmTest", "connectedAndroidDeviceTest", "testAndroidHostTest")

tasks.configureEach {
    // 2. Check if the current task is in our list OR is a standard Test task
    // We use the full class name for the Android task to avoid import issues
    val isExecutionTask = targetTaskNames.contains(name) ||
            this is Test ||
            this.javaClass.name.contains("DeviceProviderInstrumentTestTask")

    // 3. Filter out the "Noise" (ignore compile, process, and prebuild tasks)
    val isNoise = name.contains("compile", ignoreCase = true) ||
            name.contains("process", ignoreCase = true) ||
            name.contains("preBuild", ignoreCase = true)

    if (isExecutionTask && !isNoise) {
        // Force the rerun behavior
        outputs.upToDateWhen { false }

        doFirst {
            val banner = "─".repeat(70)
            println("\n$banner")
            // I deemed this emoji mandatory
            println("🚀 RUNNING: ${path.uppercase()}")
            println(banner)
        }

        // 2. The "Detailed Stats" Logic (Refactored)
        // We only add the listener if the task is a standard 'Test' task
        if (this is Test) {
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(suite: TestDescriptor) {}
                override fun afterTest(suite: TestDescriptor, result: TestResult) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    if (suite.parent == null) {
                        val total = result.testCount
                        val passed = result.successfulTestCount
                        val failed = result.failedTestCount
                        val skipped = result.skippedTestCount

                        // Choose the vibe based on the outcome
                        val icon = if (failed > 0) "❌" else "✅"
                        val resultText =
                            if (failed > 0) "TEST SUITE FAILED" else "TEST SUITE PASSED"

                        println(
                            """
        
        ╔══════════════════════════════════════════════════════════╗
        ║  $icon  $resultText
        ╠══════════════════════════════════════════════════════════╣
        ║  📊  TOTAL: $total |  ✅  PASSED: $passed |  ❌  FAILED: $failed  
        ╚══════════════════════════════════════════════════════════╝
    """.trimIndent()
                        )
                    }
                }
            })
        }

        doLast {
            val border = "=".repeat(70)
            println("\n$border")
            println("FINISH  : ${name.uppercase()} ☕")
            println("$border\n")
            println()
            println()
            println()
        }
    }
}

tasks.register("verify") {
    group = "verification"
    description = "Runs the Big Three test suites (JVM, Host, and Device)."

    // We target the lifecycle tasks which pull in the sub-tasks
    dependsOn(":composeApp:allTests", ":composeApp:connectedAndroidDeviceTest")
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Wipes the slate clean and runs all tests."

    dependsOn("clean", "verify")
}

tasks.register("verifyLocal") {
    group = "verification"
    description = "Runs JVM and Host tests only, skipping physical device tests."

    // This pulls in jvmTest and testAndroidHostTest via the KMP lifecycle
    dependsOn(":composeApp:allTests")
}

tasks.register("verifyLocalAll") {
    group = "verification"
    description = "Wipes the build and runs JVM + Host tests from a blank slate."

    // The chain: Clean -> All Local Unit Tests
    dependsOn("clean", "verifyLocal")
}


// =====================================================================