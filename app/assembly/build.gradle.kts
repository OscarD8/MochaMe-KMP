plugins {
    id("mocha.convention.assembler")
}

kotlin {
    android { namespace = "com.mochame.app.assembly" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:sync-api"))
            implementation(project(":node"))
            implementation(project(":sync-engine"))
            implementation(project(":core:logger"))
            implementation(project(":core:annotations"))
            implementation(project(":core:utils"))

            implementation(project(":feature:bio"))
            implementation(project(":feature:telemetry"))
            implementation(project(":feature:resonance"))
        }
    }
}

