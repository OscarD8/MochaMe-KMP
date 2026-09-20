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


    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.mochame.server.ServerMainKt")
}

tasks.test {
    useJUnitPlatform()
}