package com.mochame.app.entry.linux

import com.mochame.utils.ui.CliScreenNavigator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.fputs
import platform.posix.stderr
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalForeignApi::class)
fun main() {
    try {
        val koinApp = initKoinCli()

        runBlocking {
            val rootMenu = koinApp.koin.get<MainMenuCliScreen>()
            val navigator = CliScreenNavigator(rootScreen = rootMenu)
            navigator.start()
        }
    } catch (e: CancellationException) {

    } catch (t: Throwable) {
        try {
            val message = "Application runtime error: ${t.message ?: t::class.simpleName}\n"
            fprintf(stderr, "%s", message.cstr)
            fflush(stderr)
        } catch (_: Throwable) {
            fputs("Application runtime error: Out of memory or critical fault.\n", stderr)
            fflush(stderr)
        }

    } finally {
        stopKoin()
    }
}