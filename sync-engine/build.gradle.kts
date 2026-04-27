plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mocha.sync" }

    linuxX64 {
        binaries.getTest("debug").linkerOpts(
            "-Wl,--allow-shlib-undefined",
            "-Wl,--unresolved-symbols=ignore-in-shared-libs",
            "-lcrypto",
            "-lpthread",
            "-ldl"
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:metadata"))
            implementation(project(":core:di-api"))
            implementation(project(":core:utils"))
            implementation(project(":core:logger"))

            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(project(":core:test:orchestrator-test"))
            implementation(project(":core:test:platform-test"))
            implementation(project(":core:test:utils-test"))
        }
    }
}
