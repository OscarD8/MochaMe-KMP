@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mokkery)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Suppress the Beta warning for expect/actual classes
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Keep specialized JUnit settings for the Host/JVM tasks
// I couldn't run all platform tests at once otherwise...
tasks.withType<Test>().configureEach {
    if (name.contains("jvm", ignoreCase = true)) {
        useJUnitPlatform()
    } else if (name.contains("Host", ignoreCase = true)) {
        useJUnit()
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
    linuxX64 {
    }

    sourceSets {
        // --- LAYER 1: PLATFORM AGNOSTIC COMPONENTS/LOGIC ---
        val commonMain by getting
        val commonTest by getting

        commonMain.dependencies {
            // Core Identity & Data
            implementation(libs.uuid)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)

            // Local-First Persistence (Native Compatible)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // Core Infrastructure
            implementation(libs.kermit)
            implementation(project(":core:di-api"))
        }

        // --- LAYER 2: COMPOSE UI BRIDGE (Y:ANDROID/JVM DESKTOP | N:LINUX/IOS NATIVE) ---
        val uiMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Compose Multiplatform Core
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)

                // Material 3 & Navigation Stack (2026 Alpha/Beta)
                implementation(libs.compose.material3)
                implementation(libs.compose.windowSizeClass)
                implementation(libs.compose.adaptive.nav.suite)
                implementation(libs.adaptive.navigation3)
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.viewmodelNavigation3)
                implementation(libs.material.icons.extended)

                // Koin UI Integration
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)

                // Preview Tools
                implementation(libs.compose.ui.tooling.preview)
            }
        }

        // --- LAYER 3: THE PLATFORM BRANCHES ---

        val androidMain by getting {
            dependsOn(uiMain)

            dependencies {
                // Platform-Specific Lifecycle & Context
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)

                // Android-Only Infrastructure
                implementation(libs.koin.android)
                implementation(libs.work.runtime.ktx)
            }
        }

        val jvmMain by getting {
            dependsOn(uiMain)

            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val linuxX64Main by getting {
            dependsOn(commonMain)
            dependencies {
                // idea is that this will never trigger, but because ive used
                // compose multiplatform plugins for practicality, linuxNative
                // needs to allow the plugin, but it is never used in compilation
                implementation(libs.compose.runtime)
            }
        }

        if (isMac) {
            val iosMain by creating {
                dependsOn(uiMain)
            }

            val iosTest by getting {
                dependsOn(commonTest)
            }
        }

        // --- LAYER 4: TESTING ---

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.kermit.test)
        }

        val androidHostTest by getting {
            dependsOn(commonTest)
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

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }

        val linuxX64Test by getting {
            dependsOn(commonTest)
        }

        val androidDeviceTest by getting {
            dependsOn(commonTest)
            dependencies {
                // THE BRAIN (Must match Host for code compatibility)
                implementation(libs.junit4)
                // These replace the need for vintageEngine on device tests
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.runner)
                // Note: Engine is provided by the Android-KMP plugin's test runner
                implementation(libs.androidx.test.core)
                implementation(libs.koin.android)
            }
        }

    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    add("kspAndroid", libs.room.compiler)
    add("kspAndroidHostTest", libs.room.compiler)

    add("kspJvm", libs.room.compiler)
    add("kspJvmTest", libs.room.compiler)

    add("kspLinuxX64", libs.room.compiler)
    add("kspLinuxX64Test", libs.room.compiler)

    // iOS targets
    val isMac = System.getProperty("os.name") == "Mac OS X"
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
val targetTaskNames =
    setOf("jvmTest", "connectedAndroidDeviceTest", "testAndroidHostTest", "linuxX64Test")


tasks.configureEach {
    val isExecutionTask = targetTaskNames.contains(name) ||
            this is Test ||
            this.javaClass.name.contains("DeviceProviderInstrumentTestTask")

    // 3. Filter out the "Noise" (ignore compile, process, and prebuild tasks)
    val isNoise = name.contains("compile", ignoreCase = true) ||
            name.contains("process", ignoreCase = true) ||
            name.contains("preBuild", ignoreCase = true)

    if (isExecutionTask && !isNoise) {
        // Force the rerun behavior?
        outputs.upToDateWhen { false }

        doFirst {
            val banner = "─".repeat(70)
            println("\n$banner")
            println("🚀 RUNNING: ${path.uppercase()}")
            println(banner)
        }

        if (this is AbstractTestTask) {
            testLogging {
                showStandardStreams = false
                showExceptions = false
                showStackTraces = false
                showCauses = false
                events(
                    TestLogEvent.FAILED
                )
            }

            // Summary per Platform
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(suite: TestDescriptor) {}
                override fun afterTest(suite: TestDescriptor, result: TestResult) {}

                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    // Only trigger for the root suite to avoid repeating the box for every class
                    if (suite.parent == null) {
                        val total = result.testCount
                        val passed = result.successfulTestCount
                        val failed = result.failedTestCount
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
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs all including device tests."

    dependsOn(
        ":composeApp:allTests",
        ":composeApp:connectedAndroidDeviceTest",
        ":composeApp:linuxX64Test"
    )
}

tasks.register("verifyLocal") {
    group = "verification"
    description =
        "Runs linuxX64Main, JVM and Host tests only, skipping physical device tests."

    // This pulls in jvmTest and testAndroidHostTest via the KMP lifecycle
    dependsOn(":composeApp:allTests")
    dependsOn(":composeApp:linuxX64Test")
}


// =====================================================================