plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.node.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":node"))
            api(libs.kotlinx.atomicfu)
            implementation(project(":core:sync-api"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}