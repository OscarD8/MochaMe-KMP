plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    androidLibrary {
        compileSdk = 36
        minSdk = 26
        namespace = "com.plcoding.kmp_gradle9_migration.composeapp"
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ui.tooling.preview.v1101)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.runtime.v1101)
            implementation(libs.foundation.v1101)
            implementation(libs.material3.v190)
            implementation(libs.ui.v1101)
            implementation(libs.components.resources.v1101)
            implementation(libs.ui.tooling.preview.v1101)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}