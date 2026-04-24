rootProject.name = "mocha-build-logic"


dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("libs") {
            from(files(settingsDir.resolve("../gradle/libs.versions.toml")))
        }
    }
}