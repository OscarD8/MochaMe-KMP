
plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.platform.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:platform"))

            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}