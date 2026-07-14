package com.mochame.gradle


import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Standardized Target Setup.
 * * Scenario A: Targets + SDKs only (Default).
 * * Scenario B: Targets + SDKs + Test Builders (Enabled via includeTestBuilders).
 */
fun KotlinMultiplatformExtension.configureTargets(
    project: Project,
    libs: VersionCatalog,
    includeTestBuilders: Boolean = false
) {
    jvm()

    // Come back to this
    linuxX64 {
        binaries.all {
            linkerOpts("-lcrypto", "-lpthread", "-ldl")
            linkerOpts("-Wl,--allow-shlib-undefined")
        }
    }

    mochaAndroid {
        compileSdk = libs.getVersionAsInt("android-sdk-compile")
        minSdk = libs.getVersionAsInt("android-sdk-min")

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.getVersionAsString("java-jvmTarget")))
        }

        androidResources {
            enable = true
        }

        if (includeTestBuilders) {
            withHostTestBuilder { }

            withDeviceTestBuilder { sourceSetTreeName = "test" }
        }
    }

    if (project.isMac) {
        iosArm64()
        iosSimulatorArm64()
    }

    if (includeTestBuilders) {
        configureTestTargets(project, libs)
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

}

/**
 * This matches the scope of 'com.android.build.api.dsl' and provides
 * a stable accessor for the 'android' KMP target.
 *  * Replaces machine-specific generated accessors with a portable extension lookup.
 *  * Why: Precompiled scripts in build-logic cannot see the 'android' block without ephemeral hashes. This provides a static, compile-time reference to an
 *  object container.
 */
fun KotlinMultiplatformExtension.mochaAndroid(
    configure: KotlinMultiplatformAndroidLibraryTarget.() -> Unit
) {
    (this as? ExtensionAware)?.extensions?.configure(
        KotlinMultiplatformAndroidLibraryTarget::class.java,
        configure
    )
        ?: error(
            "'KotlinMultiplatformAndroidLibraryTarget' extension not found on Kotlin extension. " +
                    "Check the Android KMP plugin."
        )
}

fun KotlinMultiplatformExtension.applyStandardDependencies(project: Project) {
    sourceSets.named("commonMain") {
        dependencies {

        }
    }
}