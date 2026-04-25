import org.gradle.api.tasks.testing.logging.TestLogEvent


// Keep specialized JUnit settings for the Host/JVM tasks
// I couldn't run all platform tests at once otherwise...
tasks.withType<Test>().configureEach {
    if (name.contains("jvm", ignoreCase = true)) {
        useJUnitPlatform()
    } else if (name.contains("Host", ignoreCase = true)) {
        useJUnit()
    }
}



// ======================== Lint KSP Race Conditions? ===========================

// 1. Fix for KSP/Lint racing conditions
// This ensures the compiler doesn't have to 'infer' the type from the filter?
val kspTasks: TaskCollection<Task> = tasks.matching {
    it.name.startsWith("ksp")
}

val lintTasks: TaskCollection<Task> = tasks.matching {
    it.name.contains("Lint", ignoreCase = true)
}

// 2. Use configureEach to lazily apply the ordering
lintTasks.configureEach {
    // 'this' is now explicitly a Task within the collection
    mustRunAfter(kspTasks)
}


// =========================== TESTING FORMATTING ===========================
val targetTaskNames =
    setOf("jvmTest", "connectedAndroidDeviceTest", "testAndroidHostTest", "linuxX64Test")


tasks.configureEach {
    val isExecutionTask = targetTaskNames.contains(name) ||
            this is Test ||
            this.javaClass.name.contains("DeviceProviderInstrumentTestTask")

    // 3. Filter out the "Noise" (ignore compile, process, and prebuild tasks)
    val isNoise = name.contains("compile", ignoreCase = true) ||
            name.contains("process", ignoreCase = true) ||
            name.contains("preBuild", ignoreCase = true)

    if (isExecutionTask && !isNoise) {
        // Force the rerun behavior?
        outputs.upToDateWhen { false }

        doFirst {
            val banner = "─".repeat(70)
            println("\n$banner")
            println("🚀 RUNNING: ${path.uppercase()}")
            println(banner)
        }

        if (this is AbstractTestTask) {
            testLogging {
                showStandardStreams = false
                showExceptions = false
                showStackTraces = false
                showCauses = false
                events(
                    TestLogEvent.FAILED
                )
            }

            // Summary per Platform
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(suite: TestDescriptor) {}
                override fun afterTest(suite: TestDescriptor, result: TestResult) {}

                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    // Only trigger for the root suite to avoid repeating the box for every class
                    if (suite.parent == null) {
                        val total = result.testCount
                        val passed = result.successfulTestCount
                        val failed = result.failedTestCount
                        val icon = if (failed > 0) "❌" else "✅"
                        val resultText =
                            if (failed > 0) "TEST SUITE FAILED" else "TEST SUITE PASSED"

                        println(
                            """
        
        ╔══════════════════════════════════════════════════════════╗
        ║  $icon  $resultText
        ╠══════════════════════════════════════════════════════════╣
        ║  📊  TOTAL: $total |  ✅  PASSED: $passed |  ❌  FAILED: $failed  
        ╚══════════════════════════════════════════════════════════╝
                """.trimIndent()
                        )
                    }
                }
            })

            doLast {
                val border = "=".repeat(70)
                println("\n$border")
                println("FINISH  : ${name.uppercase()} ☕")
                println("$border\n")
                println()
                println()
                println()
            }
        }
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs all including device tests."

    dependsOn(
        ":composeApp:allTests",
        ":composeApp:connectedAndroidDeviceTest",
        ":composeApp:linuxX64Test"
    )
}

tasks.register("verifyLocal") {
    group = "verification"
    description =
        "Runs linuxX64Main, JVM and Host tests only, skipping physical device tests."

    // This pulls in jvmTest and testAndroidHostTest via the KMP lifecycle
    dependsOn(":composeApp:allTests")
    dependsOn(":composeApp:linuxX64Test")
}