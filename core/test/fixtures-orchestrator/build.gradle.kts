plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.orchestrator.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:orchestrator"))
            implementation(project(":core:utils"))
            implementation(project(":core:test:fixtures-metadata"))
            implementation(project(":core:test:testlogger"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
