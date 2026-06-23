
plugins {
    id("mocha.convention.feature")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLint) // WHAT IS THIS
    alias(libs.plugins.mokkery)
}


kotlin {
    android { namespace = "com.mochame.bio" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:contract"))
            implementation(project(":core:platform"))
            implementation(project(":core:utils"))

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)
        }
    }
}
