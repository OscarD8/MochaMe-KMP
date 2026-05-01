
plugins {
    id("mocha.convention.logic")
}

kotlin {
    android { namespace = "com.mochame.system.orchestrator" }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:utils"))
            implementation(project(":system:infra"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":core:test:fixtures-contract"))
        }
    }

}