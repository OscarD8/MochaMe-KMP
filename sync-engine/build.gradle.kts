import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.room)
    alias(libs.plugins.koin.compiler)
    id("mocha.test.conventions")
    id("mocha.room")
}

kotlin {
    jvm()
    linuxX64 {
        binaries {
            // Find the automatically created 'test' binary
            getTest("debug").linkerOpts(
                "-Wl,--allow-shlib-undefined",
                "-Wl,--unresolved-symbols=ignore-in-shared-libs",
                "-lcrypto",
                "-lpthread",
                "-ldl"
            )
        }
    }

    android {
        namespace = "com.mocha.sync"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder { }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
        }
    }

    val isMac = System.getProperty("os.name") == "Mac OS X"

    if (isMac) {
        val iosTargets = listOf(
            iosArm64(),
            iosSimulatorArm64()
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:metadata"))
            implementation(project(":core:di-api"))
            implementation(project(":core:utils"))
            implementation(project(":core:logger"))

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(project(":core:test:support"))
            implementation(project(":core:test:orchestrator-test"))
            implementation(project(":core:test:platform-test"))
        }

        val linuxX64Test by getting {
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }

        val androidHostTest by getting {
        }

        val androidDeviceTest by getting {
        }

        if (isMac) {
        }

    }

    compilerOptions {
        // Suppresses the 'expect/actual classes are in Beta' warning
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}


koinCompiler {
    userLogs = true           // Log component detection
    debugLogs = true          // Log internal processing (verbose)
    unsafeDslChecks = true    // Validates create() is the only instruction in lambda (default: true)
    skipDefaultValues = true  // Skip injection for parameters with default values (default: true)
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspAndroidHostTest", libs.room.compiler)

    add("kspJvm", libs.room.compiler)
    add("kspJvmTest", libs.room.compiler)

    add("kspLinuxX64", libs.room.compiler)
    add("kspLinuxX64Test", libs.room.compiler)

    val isMac = System.getProperty("os.name") == "Mac OS X"
    if (isMac) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
        add("kspIosSimulatorArm64Test", libs.room.compiler)
    }
}
