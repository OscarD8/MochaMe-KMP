plugins {
    id("mocha.convention.assembler")
}

kotlin {
    android { namespace = "com.mochame.schema" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:contract"))
            implementation(project(":core:sync-api"))
            implementation(project(":core:platform"))
            implementation(project(":node"))
            implementation(project(":sync-engine"))
            implementation(project(":mocha:feature:bio"))
            implementation(project(":mocha:feature:telemetry"))
            implementation(project(":mocha:feature:resonance"))
        }
    }
}

