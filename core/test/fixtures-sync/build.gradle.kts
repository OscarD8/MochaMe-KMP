plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.sync.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:sync-api"))
            implementation(project(":core:test:support"))
            implementation(project(":core:test:fixtures-utils"))
            implementation(project(":core:test:fixtures-platform"))
            implementation(project(":core:test:fixtures-node"))
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}