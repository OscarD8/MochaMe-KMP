plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "com.mochame.app.entry.linux.main"

                linkerOpts("-lcrypto", "-lpthread", "-ldl")
                linkerOpts("-Wl,--allow-shlib-undefined")
            }
        }
    }

    sourceSets {
        linuxX64Main.dependencies {
            implementation(project(":app:assembly"))
            implementation(project(":feature:bio"))

            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
