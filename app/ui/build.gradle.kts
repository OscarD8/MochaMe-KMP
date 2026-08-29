plugins {
    id("mocha.convention.ui")
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.app.ui" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:schema"))

            implementation(project(":node"))
            implementation(project(":sync-engine"))

            implementation(project(":core:platform"))
            implementation(project(":core:annotations"))
            implementation(project(":core:utils"))
            implementation(project(":core:logger"))

            implementation(project(":feature:bio"))
            implementation(project(":feature:bio:ui"))

        }
    }
}
