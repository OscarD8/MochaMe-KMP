plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.utils.test" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:utils"))
        }
    }
}