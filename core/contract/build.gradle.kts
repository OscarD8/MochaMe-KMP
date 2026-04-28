plugins {
    id("mocha.convention.provider")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mocha.contract" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            api(libs.koin.annotations)
        }
    }
}


