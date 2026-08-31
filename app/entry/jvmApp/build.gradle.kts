import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.configure {
            dependencies {
                implementation(project(":app:ui"))

                implementation(compose.desktop.currentOs)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)

                implementation(libs.koin.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.mochame.app.entry.jvm.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "MochaMe"
            packageVersion = "1.0.0"
            description = "MochaMe Local-First"
            vendor = "MochaMe"

            linux {
                shortcut = true
                menuGroup = "Utility"
            }
        }
    }
}
