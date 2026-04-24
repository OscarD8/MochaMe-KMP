plugins {
    `kotlin-dsl`
}

dependencies {
    api(libs.koin.compiler.gradle.plugin)
    api(libs.ksp.gradle.plugin)
    api(libs.kotlin.gradle.plugin)
    api(libs.room.gradle.plugin)
}