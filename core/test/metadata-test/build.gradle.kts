plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.mochame.metadata.test"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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
            api(project(":core:metadata"))
            implementation(project(":core:di-api"))
            implementation(project(":core:utils"))

            api(libs.koin.core)
            api(libs.koin.test)

            implementation(libs.room.runtime)
            implementation(libs.kermit)
        }
    }
}