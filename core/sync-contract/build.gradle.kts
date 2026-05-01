plugins {
    id("mocha.convention.provider")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.sync.contract" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
        }
    }
}