rootProject.name = "MochaMe"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("build-logic")

include(":composeApp")
include(":app:entry:androidApp")
include(":app:entry:linuxCliApp")
include(":app:assembly")
include(":core:platform")
include(":core:test:support")
include(":core:test:metadata-test")
include(":core:orchestrator")
include(":core:test:platform-test")
include(":core:utils")
include(":core:di-api")
include(":core:metadata")
include(":sync-engine")
include(":mocha:mocha-feature:bio")
include(":mocha:mocha-ui")
include(":mocha:mocha-schema")
