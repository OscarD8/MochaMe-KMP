package com.mochame.app

import com.mochame.app.di.initKoin

fun main() {
    println("--- Mocha Me: Linux Native Kernel Starting ---")

    // 1. Initialize Koin for the Native target
    // We pass "LINUX" to fork any platform-specific DI logic
    val koinApplication = initKoin("LINUX")

    println("Kernel Ready. Systems online.")

    // For a test/CLI tool, you might run a loop or wait for a signal
}