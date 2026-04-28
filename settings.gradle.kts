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
    includeBuild("build-logic")
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


include(":composeApp")
include(":app:entry:androidApp")
include(":app:entry:linuxCliApp")
include(":app:entry:jvmApp")
include(":app:assembly")

include(":core:contract")
include(":core:logger")
include(":core:utils")
include(":core:platform")

include("system:infra")
include("system:orchestrator")

include(":core:test:support")
include(":core:test:test-logger")
include(":core:test:fixtures-contract")
include(":core:test:fixtures-system-orchestrator")
include(":core:test:fixtures-system-infra")
include(":core:test:fixtures-utils")
include(":core:test:fixtures-platform")

include(":sync-engine")

include(":mocha:mocha-feature:bio")
include(":mocha:mocha-ui")
include(":mocha:mocha-schema")
