package com.mochame.app.entry.jvm

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochame.app.ui.MochaComposeAppShell
import com.mochame.app.ui.di.initKoinCompose
import java.awt.Dimension

fun main() {
    initKoinCompose()

    application {
        val windowState = rememberWindowState(
            width = 1024.dp,
            height = 768.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "MochaMe"
        ) {
            window.minimumSize = Dimension(480, 560)
            MochaComposeAppShell()
        }
    }
}