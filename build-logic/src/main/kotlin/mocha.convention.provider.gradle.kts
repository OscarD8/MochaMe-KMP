import com.mochame.gradle.configureTargets
import com.mochame.gradle.libs
import com.mochame.gradle.standardConfigurations

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.insert-koin.compiler.plugin")
}

standardConfigurations()

kotlin {
    configureTargets(
        project = this@Project,
        libs = libs
    )

    sourceSets {
        val commonMainProvider = named("commonMain")
        val isFixture = project.path.startsWith(":core:test:fixtures-")

        if (isFixture) {
            commonMainProvider.configure {
                dependencies {
                    implementation(project(":core:annotations"))
                    api(project(":core:test:support"))
                }
            }
        }
    }
}