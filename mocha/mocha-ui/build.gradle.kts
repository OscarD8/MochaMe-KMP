import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mocha.convention.provider")
}


kotlin {

    android {
        namespace = "com.mochame.ui"
    }
}
