plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.utils.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:utils"))
            implementation(libs.kotlinx.atomicfu)
        }
    }
}