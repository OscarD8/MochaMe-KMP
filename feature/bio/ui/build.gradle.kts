plugins {
    id("mocha.convention.ui")
    alias(libs.plugins.koin.compiler)
}

kotlin {
    android { namespace = "com.mochame.bio.ui" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(project(":feature:bio"))
            implementation(project(":core:sync-api"))

            implementation(libs.koin.annotations)
        }
    }
}
