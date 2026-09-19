plugins {
    alias(libs.plugins.koin.compiler)
    kotlin("jvm")
    id("application")
}

dependencies {
    implementation(project(":core:sync-api"))
    implementation(project(":core:annotations"))
    implementation(project(":core:logger"))

    implementation(libs.hikaricp)
    implementation(libs.bundles.ktor.server)
    implementation(libs.sqlite.jdbc)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.test.junit.jupiter)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.mochame.server.ServerMainKt")
}