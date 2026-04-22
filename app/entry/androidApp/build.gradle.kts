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
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":mocha:mocha-schema"))
    implementation(project(":core:platform"))
    implementation(project(":core:logger"))
    implementation(project(":core:di-api"))

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose)
    implementation(libs.material.v1120)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
//    implementation(libs.sqlite.bundled)


    // Testing
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.testExt.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
}