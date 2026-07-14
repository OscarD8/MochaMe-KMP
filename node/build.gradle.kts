plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.node" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.uuid)
        }
        commonTest.dependencies {
            implementation(project(":core:test:fixtures-utils"))
        }
    }
}