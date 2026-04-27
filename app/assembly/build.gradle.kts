plugins {
    id("mocha.convention.assembler")
}

kotlin {
    android { namespace = "com.mochame.app.assembly" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sync-engine"))
            implementation(project(":core:orchestrator"))
            implementation(project(":core:platform"))
            implementation(project(":core:metadata"))
            implementation(project(":core:utils"))
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":core:test:support"))
            implementation(project(":core:test:fixtures-orchestrator"))
        }
    }
}