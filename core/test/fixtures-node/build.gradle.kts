plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.node.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":node"))
            implementation(libs.kotlinx.atomicfu)
            implementation(project(":core:sync-api"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}