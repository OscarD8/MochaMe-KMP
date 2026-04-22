import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    linuxX64 {
        compilations.getByName("main") {
            val openssl by cinterops.creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/openssl.def"))
            }
        }
        binaries.all {
            linkerOpts("-lcrypto", "-lpthread", "-ldl")
            linkerOpts("-Wl,--allow-shlib-undefined")
        }
    }

    android {
        namespace = "com.mocha.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder { }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        // Enable resources for Robolectric/Compose tests
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val isMac = System.getProperty("os.name") == "Mac OS X"

    if (isMac) {
        // 1. Define the targets
        val iosTargets = listOf(
            iosArm64(),
            iosSimulatorArm64()
        )

//        // 2. Configure the targets
//        iosTargets.forEach { iosTarget ->
//            iosTarget.binaries.framework {
//                baseName = "ComposeApp"
//                isStatic = true
//            }
//        }
    }


    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:di-api"))
            implementation(project(":core:logger"))
            implementation(project(":core:metadata"))
            api(project(":core:utils"))

            implementation(libs.uuid)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.io.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.work.runtime.ktx)
//            implementation(libs.koin.annotations)
//            implementation(libs.koin.core)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }

        val linuxX64Main by getting {
            dependencies {

            }
        }


        commonTest.dependencies {
            implementation(project(":core:test:support"))
        }

        val commonTest by getting

        val linuxX64Test by getting {
            dependsOn(commonTest)
        }

        jvmTest.dependencies {
            implementation(libs.test.junit.jupiter)
        }

        val androidHostTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.junit4)
                implementation(libs.test.robolectric)
                runtimeOnly(libs.junit.vintage.engine)
                implementation(libs.androidx.test.core)
            }
        }

        val androidDeviceTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.junit4)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.koin.android)
            }
        }

        if (isMac) {
            iosMain {
                dependencies {
                }
            }

            val iosTest by getting {
                dependsOn(commonTest)
            }
        }

    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspAndroidHostTest", libs.room.compiler)

    add("kspJvm", libs.room.compiler)
    add("kspJvmTest", libs.room.compiler)

    add("kspLinuxX64", libs.room.compiler)
    add("kspLinuxX64Test", libs.room.compiler)

    val isMac = System.getProperty("os.name") == "Mac OS X"
    if (isMac) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
        add("kspIosSimulatorArm64Test", libs.room.compiler)
    }
}