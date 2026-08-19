plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.utils.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:utils"))
            api(project(":core:sync-api"))
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}