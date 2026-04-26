plugins {
    id("mocha.convention.provider")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mocha.metadata" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}


