plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mocha.logger.test" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:di-api"))
            implementation(libs.kotlinx.datetime)

            api(project(":core:logger"))
            api(libs.kermit.test)
        }
    }
}
