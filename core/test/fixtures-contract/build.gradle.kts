plugins {
    id("mocha.convention.provider")
}


kotlin {
    android { namespace = "com.mochame.contract.fixtures" }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:contract"))
            implementation(project(":core:utils"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}