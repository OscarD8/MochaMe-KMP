import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mokkery)
}

kotlin {
    jvm()
    linuxX64()


    android {
        namespace = "com.mocha.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

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
        val iosTargets = listOf(
            iosArm64(),
            iosSimulatorArm64()
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.koin.test)
            api(kotlin("test"))
            api(libs.kotlinx.serialization.protobuf)
            api(libs.room.runtime)
            api(libs.kermit.test)
            api(libs.turbine)
            implementation(libs.kermit)
            implementation(libs.koin.annotations)
            implementation(libs.koin.core)
            implementation(libs.sqlite.bundled)
            implementation(project(":core:platform"))
            implementation(project(":core:di-api"))
            implementation(project(":core:utils"))
        }

        androidMain.dependencies {
            api(libs.test.robolectric)
            api(libs.androidx.test.core)
            api(libs.junit4)
            api(libs.test.mockk)
            implementation(libs.androidx.junit.ktx)
            runtimeOnly(libs.junit.vintage.engine)
        }

        val jvmMain by getting {
            dependencies {
                api(libs.test.junit.jupiter)
            }
        }

        val linuxX64Main by getting {
            dependencies {

            }
        }

        if (isMac) {
            val iosMain by creating {

            }
        }
    }

}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    add("kspAndroid", libs.room.compiler)

    add("kspJvm", libs.room.compiler)

    add("kspLinuxX64", libs.room.compiler)

    val isMac = System.getProperty("os.name") == "Mac OS X"
    if (isMac) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }

}