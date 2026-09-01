plugins {
    alias(libs.plugins.koin.compiler)
    kotlin("jvm")
    id("application")
}

dependencies {
    implementation(project(":core:sync-api"))
    implementation(project(":core:annotations"))
    implementation(project(":core:logger"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.mochame.server.ServerMainKt")
}