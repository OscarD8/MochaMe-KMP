plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.test.support" }

    sourceSets {
        commonMain.dependencies {
            api(kotlin("test"))
            api(libs.koin.test)
            api(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.coroutines.test)
            api(libs.kermit.test)
            api(libs.turbine)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            implementation(project(":core:platform"))
            api(project(":core:test:utils-test"))
            api(project(":core:di-api"))
            api(project(":core:logger"))
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
    }
}
