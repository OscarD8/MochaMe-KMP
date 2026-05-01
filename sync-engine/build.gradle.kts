plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mocha.sync" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:utils"))
            implementation(project(":system:infra"))

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(project(":core:test:fixtures-contract"))
            implementation(project(":core:test:fixtures-system-infra"))
            implementation(project(":core:test:fixtures-utils"))
            implementation(project(":core:test:fixtures-platform"))
        }
    }
}
