
plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.resonance" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mocha:feature:telemetry"))
        }
    }
}