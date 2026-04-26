
plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.platform.test" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:test:support"))
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            api(project(":core:platform"))

            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}