plugins {
    id("mocha.convention.assembler")
}

kotlin {
    android { namespace = "com.mochame.app.assembly" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sync-engine"))
            implementation(project(":core:platform"))
            implementation(project(":system:infra"))
            implementation(project(":system:orchestrator"))
            implementation(project(":core:contract"))
            implementation(project(":core:utils"))
            implementation(project(":core:logger"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}