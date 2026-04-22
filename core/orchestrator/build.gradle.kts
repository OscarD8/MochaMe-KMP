import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    android {
        namespace = "com.mochame.platform.orchestrator"
        compileSdk = 36
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm()
    linuxX64()

    val isMac = System.getProperty("os.name") == "Mac OS X"
    if (isMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(project(":core:metadata"))
            implementation(project(":sync-engine"))
            implementation(project(":core:di-api"))

            api(libs.kotlinx.coroutines.core)
        }
    }
}