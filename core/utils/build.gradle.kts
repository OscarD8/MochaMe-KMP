plugins {
    id("mocha.convention.logic")
}

kotlin {
    android { namespace = "com.mocha.core.utils" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.uuid)
        }
    }
}