plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.orchestrator.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":system:orchestrator"))
            implementation(project(":core:utils"))
            implementation(project(":core:test:fixtures-contract"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
