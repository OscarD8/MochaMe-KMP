import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.mochame.gradle.applyStandardDependencies
import com.mochame.gradle.configureTargets
import com.mochame.gradle.getLibrary
import com.mochame.gradle.libs
import com.mochame.gradle.standardConfigurations
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
    configureTargets(
        project = this@Project,
        libs = libs,
        includeTestBuilders = true
    )

    applyStandardDependencies(this@Project)

    sourceSets {
        val commonMainProvider = named("commonMain")
        commonMainProvider.configure {
            dependencies {
                implementation(libs.getLibrary("room-runtime"))
                implementation(project(":core:contract"))
                implementation(project(":core:sync-contract"))
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val roomCompiler = libs.getLibrary("room-compiler")

    kotlin.targets.configureEach {
        if (this !is KotlinMetadataTarget) {
            val targetName = name.replaceFirstChar { it.uppercase() }

            val kspNames = if (name == "android") {
                listOf("kspAndroidHostTest", "kspAndroidDeviceTest")
            } else {
                listOf("ksp${targetName}Test")
            }

            kspNames.forEach { kspName ->
                configurations.findByName(kspName)?.let { config ->
                    project.dependencies.add(config.name, roomCompiler)
                }
            }
        }
    }
}

// To resolve:
//     Reason: Task ':mocha:feature:bio:generateAndroidHostTestLintModel' uses this output of task
//     ':mocha:feature:bio:kspAndroidHostTest' without declaring an explicit or implicit dependency.
//     This can lead to incorrect results being produced, depending on what order the tasks are executed.
//
// Gradle 9+ implicit dependency fix: KSP output directories are consumed by AGP's
// lint tasks without a declared task graph edge. This causes non-deterministic build
// results when Gradle parallelises the configuration phase.
//
// Root cause: AGP's LintModelWriterTask and AndroidLintAnalysisTask read from the
// KSP-generated source directories but declare no dependency on the KSP tasks that
// produce them. Neither AGP nor KSP expose a stable public API to wire this edge
// directly.
//
// Approach: mustRunAfter enforces ordering when both tasks are scheduled without
// forcing KSP to run on lint-only build invocations. String-based task name matching
// is used in preference to internal AGP class references (e.g. AndroidLintAnalysisTask,
// LintModelWriterTask) which live under .internal. packages with no stability contract.
//
// Fragility surface: AGP task naming conventions. If AGP renames these tasks in a
// future version, the mustRunAfter registration silently becomes a no-op rather than
// failing the build. Verify after any AGP version bump by running:
//   ./gradlew :mocha:feature:bio:testAndroidHost --rerun-tasks
// and checking for implicit dependency warnings in the build output.
plugins.withId("com.google.devtools.ksp") {
    val kspHostTest = tasks.matching { it.name == "kspAndroidHostTest" }
    val kspDeviceTest = tasks.matching { it.name == "kspAndroidDeviceTest" }

    tasks.configureEach {
        when (name) {
            "generateAndroidHostTestLintModel",
            "lintAnalyzeAndroidHostTest" -> mustRunAfter(kspHostTest)

            "generateAndroidDeviceTestLintModel",
            "lintAnalyzeAndroidDeviceTest" -> mustRunAfter(kspDeviceTest)
        }
    }
}