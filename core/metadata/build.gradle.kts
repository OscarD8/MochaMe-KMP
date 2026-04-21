import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    android {
        namespace = "com.mocha.metadata"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder { }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()
    linuxX64()

    sourceSets {

        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:di-api"))
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.koin.annotations)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.uuid)
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
