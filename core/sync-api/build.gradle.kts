plugins {
    id("mocha.convention.provider")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.sync.api" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)

            implementation(project(":core:logger"))
            implementation(project(":core:annotations"))
        }
    }
}