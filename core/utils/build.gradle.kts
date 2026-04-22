import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    android {
        namespace = "com.mocha.core.utils"
        compileSdk = 36

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
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
            implementation(libs.kotlinx.datetime)
            api(libs.kermit)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.io.core)
            api(libs.koin.core)
            api(libs.koin.annotations)
        }
    }
}