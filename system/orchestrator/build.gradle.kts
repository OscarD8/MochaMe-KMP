@file:OptIn(KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi

plugins {
    id("mocha.convention.feature")
}

kotlin {
    android { namespace = "com.mochame.system.orchestrator" }

    linuxX64 {
        binaries.all {
            linkerOpts("-lcrypto", "-lpthread", "-ldl")
            linkerOpts("-Wl,--allow-shlib-undefined")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":core:test:fixtures-contract"))
        }
    }

}