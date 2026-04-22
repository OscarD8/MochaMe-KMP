plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "com.mochame.cli.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sync-engine"))
            implementation(project(":app:assembly"))
            implementation(project(":core:platform"))
            implementation(project(":mocha:mocha-schema"))
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)

        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}