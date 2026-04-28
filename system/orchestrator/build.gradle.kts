plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.system.orchestrator" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":core:test:fixtures-contract"))
        }
    }
}