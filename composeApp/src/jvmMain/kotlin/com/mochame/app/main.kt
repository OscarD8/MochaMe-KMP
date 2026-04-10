package com.mochame.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mochame.app.di.initKoin

fun main() = application {
    /**
     * THE LINUX EXPRESSION
     * No briefing needed here. The Ribosome starts with default instructions
     * because on the Linux planet, we don't need a special security key
     * to access the filesystem.
     */
    initKoin("JVM")

    Window(
        onCloseRequest = ::exitApplication,
        title = "Mocha Me: Linux Kernel Test"
    ) {
        App()
    }
}