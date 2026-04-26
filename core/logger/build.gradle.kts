plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mocha.logger" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:di-api"))
            api(libs.kermit)
            implementation(libs.kotlinx.datetime)
        }
    }
}
