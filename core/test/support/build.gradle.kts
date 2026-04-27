plugins {
    id("mocha.convention.provider")
}

kotlin {
    android { namespace = "com.mochame.test.support" }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.room.runtime)

            implementation(project(":core:platform"))
            implementation(project(":core:di-api"))
            implementation(project(":core:test:fixtures-utils"))
            api(project(":core:test:testlogger"))
        }

        androidMain.dependencies {
            implementation(libs.test.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.junit4)
            implementation(libs.test.mockk)
            implementation(libs.androidx.junit.ktx)
        }
    }
}
