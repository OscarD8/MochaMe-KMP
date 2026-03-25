package com.mochame.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mochame.app.di.initKoin
import com.mochame.app.ui.ProofOfLifeScreen
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.compose.viewmodel.koinViewModel

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