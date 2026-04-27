
plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.platform.fixtures" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:di-api"))
            implementation(project(":core:test:testlogger"))
            api(project(":core:platform"))

            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}