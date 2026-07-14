plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.logger" }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            api(libs.kermit)

            api(project(":core:annotations"))
        }
    }
}
