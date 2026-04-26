plugins {
    id("mocha.convention.logic")
    alias(libs.plugins.androidLint)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    linuxX64 {
        compilations.getByName("main") {
            val openssl by cinterops.creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/openssl.def"))
            }
        }
        binaries.all {
            linkerOpts("-lcrypto", "-lpthread", "-ldl")
            linkerOpts("-Wl,--allow-shlib-undefined")
        }
    }

    android { namespace = "com.mochame.platform" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            implementation(project(":core:metadata"))
            api(project(":core:utils"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.uuid)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.work.runtime.ktx)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }

    }
}
