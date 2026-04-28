import com.mochame.gradle.applyStandardDependencies
import com.mochame.gradle.configureTargets
import com.mochame.gradle.getLibrary
import com.mochame.gradle.libs
import com.mochame.gradle.standardConfigurations
import gradle.kotlin.dsl.accessors._aea48d7fc91284f74eb23540e788dcf0.room
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.insert-koin.compiler.plugin")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

standardConfigurations()

kotlin {
    sourceSets {
        val commonMainProvider = named("commonMain")

        commonMainProvider.configure {
            dependencies {
                implementation(libs.getLibrary("room-runtime"))
            }
        }
    }

    configureTargets(
        project = this@Project,
        libs = libs
    )

    applyStandardDependencies(this@Project)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val roomCompiler = libs.getLibrary("room-compiler")

    kotlin.targets.configureEach {
        val kspConfigName = if (this is KotlinMetadataTarget) {
            "kspCommonMainMetadata"
        } else {
            "ksp${name.replaceFirstChar { it.uppercase() }}"
        }

        configurations.matching { it.name == kspConfigName }.configureEach {
            project.dependencies.add(this.name, roomCompiler)
        }
    }
}