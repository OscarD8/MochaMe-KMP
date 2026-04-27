plugins {
    id("mocha.convention.provider")
}


kotlin {
    android { namespace = "com.mochame.metadata.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:metadata"))
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}