import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
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

    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            implementation(libs.koin.core)

            implementation(project(":core:platform"))
            implementation(project(":sync-engine"))
            implementation(project(":mocha:mocha-feature:bio"))
//            implementation(project(":mocha-feature:telemetry"))
            implementation(project(":composeApp"))
            implementation(project(":core:metadata"))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // 3. KSP must run on every target to generate the actual Room implementations
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspLinuxX64", libs.room.compiler)
}

