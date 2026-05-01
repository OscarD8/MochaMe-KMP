plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.telemetry" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mocha:mocha-feature:bio"))
            implementation(libs.uuid)
        }
    }
}