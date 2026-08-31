plugins {
    id("mocha.convention.ui")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android { namespace = "com.mochame.app.ui" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:assembly"))
            implementation(project(":core:utils"))
            implementation(project(":core:platform"))
            implementation(project(":core:logger"))

            implementation(project(":feature:bio"))
            implementation(project(":feature:bio:ui"))
        }
    }
}
