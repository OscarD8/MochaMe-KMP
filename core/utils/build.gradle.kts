plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mocha.core.utils" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:di-api"))
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.room.runtime)
        }
    }
}