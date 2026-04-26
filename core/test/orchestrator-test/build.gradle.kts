plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.orchestrator.test" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:metadata"))
            api(project(":core:orchestrator"))
            implementation(project(":core:test:support"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
