
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
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(project(":core:test:fixtures-sync"))
            implementation(project(":core:test:fixtures-utils"))
            implementation(project(":core:test:fixtures-platform"))
            implementation(project(":core:test:fixtures-node"))
        }
    }
}
