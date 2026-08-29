package com.mochame.app.entry.linux

import com.mochame.utils.cli.CliScreenNavigator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr

@OptIn(ExperimentalForeignApi::class)
fun main() {
    try {
        val koinApp = initKoinCli()

        runBlocking {
            val rootMenu = koinApp.koin.get<MainMenuCliScreen>()
            val navigator = CliScreenNavigator(rootScreen = rootMenu)
            navigator.start()
        }
    } catch (t: Throwable) {
        fprintf(stderr, "%s\n", "Application runtime error: \" + ${t.message}")
        fflush(stderr)
    } finally {
        stopKoin()
    }
}