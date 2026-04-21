import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.room)
}


kotlin {
    jvm()
    linuxX64()

    android {
        namespace = "com.mocha.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder { }

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