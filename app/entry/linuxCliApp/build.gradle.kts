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
                entryPoint = "com.mochame.app.entry.linux"
            }
        }
    }

    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":app:schema"))
            implementation(project(":feature:bio"))
            implementation(project(":node"))
            implementation(project(":sync-engine"))

//            implementation(project(":mocha:feature:bio"))

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}