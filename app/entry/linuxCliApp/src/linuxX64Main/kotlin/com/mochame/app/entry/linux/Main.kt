package com.mochame.app.entry.linux

import com.mochame.annotations.AppBackgroundScope
import com.mochame.utils.ui.CliScreenNavigator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.fputs
import platform.posix.stderr
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val koinApp = initKoinCli()

    try {
        runBlocking {
            val rootMenu = koinApp.koin.get<MainMenuCliScreen>()
            val navigator = CliScreenNavigator(rootScreen = rootMenu)
            navigator.start()
        }
    } catch (_: CancellationException) {

    } catch (t: Throwable) {
        try {
            val fullError = buildString {
                appendLine("=== RUNTIME CRASH ===")
                appendLine(t.stackTraceToString())
                var cause = t.cause
                while (cause != null) {
                    appendLine("Caused by: ${cause::class.simpleName}: ${cause.message}")
                    cause = cause.cause
                }
            }
            fprintf(stderr, "%s", fullError.cstr)
            fflush(stderr)
        } catch (_: Throwable) {
            fputs("Application runtime error: Out of memory or critical fault.\n", stderr)
            fflush(stderr)
        }
    } finally {
        koinApp.koin.getOrNull<CoroutineScope>(
            qualifier = named("AppBackgroundScope")
        )?.cancel()
        stopKoin()
    }
}