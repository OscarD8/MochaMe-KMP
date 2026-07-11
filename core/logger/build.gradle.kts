plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mocha.logger" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:contract"))
            implementation(libs.kotlinx.datetime)
            api(libs.kermit)
        }
    }
}
