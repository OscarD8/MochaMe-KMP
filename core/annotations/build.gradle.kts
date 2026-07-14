plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.annotations" }

    sourceSets {
        commonMain.dependencies {
            api(libs.koin.core)
            api(libs.koin.annotations)
        }
    }
}


