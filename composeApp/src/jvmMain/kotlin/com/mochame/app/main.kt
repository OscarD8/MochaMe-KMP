package com.mochame.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mochame.app.di.initKoin
import com.mochame.app.ui.ProofOfLifeScreen
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.compose.viewmodel.koinViewModel

fun main() = application {
    // 1. Initialize Koin for the Desktop environment
    // Note: No androidContext() here, so it uses the getDesktopDatabase() actual.
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Mocha Me: Linux Kernel Test"
    ) {
        // 2. Launch the Shared UI
        val viewModel = koinViewModel<ProofOfLifeViewModel>()
        ProofOfLifeScreen(viewModel)
    }
}