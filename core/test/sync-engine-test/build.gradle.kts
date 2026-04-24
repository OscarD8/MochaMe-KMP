import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    jvm()
    linuxX64()

    android {
        namespace = "com.mocha.sync.test"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
            api(project(":core:di-api"))
            api(project(":sync-engine"))
            api(project(":core:test:support"))
        }
    }

}

