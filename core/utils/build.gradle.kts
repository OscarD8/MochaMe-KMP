plugins {
    id("mocha.convention.logic")
}

kotlin {
    android { namespace = "com.mochame.core.utils" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kermit)
        }
    }
}