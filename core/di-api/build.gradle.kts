plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mocha.di" }

    sourceSets {
        commonMain.dependencies {
            api(libs.koin.core)
            api(libs.koin.annotations)
        }
    }
}

