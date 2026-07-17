plugins {
    id("mocha.convention.feature")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.sync" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.atomicfu)
            implementation(project(":core:test:fixtures-node"))
            implementation(project(":core:test:fixtures-utils"))
            implementation(project(":core:test:fixtures-platform"))
        }
    }
}
