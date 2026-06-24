plugins {
    id("mocha.convention.provider")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.sync.contract" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:contract"))
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}