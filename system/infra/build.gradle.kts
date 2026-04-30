
plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.system.infra" }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
        }
        commonTest.dependencies {
            implementation(project(":core:platform"))
        }
    }
}