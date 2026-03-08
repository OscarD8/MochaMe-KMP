import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

configure<ApplicationExtension> {
    namespace = "com.mochame.androidapp"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.mochame.androidapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":composeApp")) // Your shared logic/UI

    // KOIN: This is the critical piece you were missing
    // It allows the Android App to provide the 'Context' to the shared Room DB
    implementation(libs.koin.android)
    implementation(libs.koin.compose) // Optional, but helps with koinViewModel()
    implementation(libs.koin.compose.viewmodel)
    // For 'koinViewModel()' inside @Composable
    implementation(libs.koin.compose)
    implementation(libs.material.v1120)

    // Standard AndroidX & Compose Shell
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)

    // Testing
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.testExt.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
}