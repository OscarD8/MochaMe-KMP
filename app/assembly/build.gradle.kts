plugins {
    id("mocha.convention.assembler")
}

kotlin {
    android { namespace = "com.mochame.app.assembly" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sync-engine"))
            implementation(project(":core:platform"))
            implementation(project(":node"))
            implementation(project(":core:annotations"))
            implementation(project(":core:utils"))
            implementation(project(":core:logger"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}