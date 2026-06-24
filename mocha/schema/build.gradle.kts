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
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        minSdk = libs.versions.android.sdk.min.get().toInt()

        withHostTestBuilder { }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        // Enable resources for Robolectric/Compose tests
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.jvmTarget.get()))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            implementation(libs.koin.core)

            implementation(project(":core:sync-api"))
            implementation(project(":core:platform"))
            implementation(project(":system:infra"))
            implementation(project(":sync-engine"))
            implementation(project(":mocha:feature:bio"))
            implementation(project(":mocha:feature:telemetry"))
            implementation(project(":mocha:feature:resonance"))
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

