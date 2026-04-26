plugins {
    id("mocha.convention.logic")
}

kotlin {
    android { namespace = "com.mochame.orchestrator" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:metadata"))
            implementation(project(":core:logger"))
            api(project(":core:di-api"))
            api(project(":core:utils"))

            implementation(libs.kotlinx.coroutines.core)
        }
    }
}