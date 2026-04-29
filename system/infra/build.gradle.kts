
plugins {
    id("mocha.convention.logic")
}

kotlin {
    android { namespace = "com.mochame.system.infra" }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:contract"))
            implementation(libs.room.runtime)
        }
    }
}