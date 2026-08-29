package com.mochame.app.entry.linux

import com.mochame.bio.cli.DailyContextCliScreen
import com.mochame.utils.cli.InteractiveScreen
import com.mochame.utils.cli.ScreenResult
import org.koin.core.annotation.Single

@Single
class MainMenuCliScreen(
    private val bioScreen: DailyContextCliScreen
) : InteractiveScreen {

    override val title: String = "Main Context Selector"

    override suspend fun renderAndHandleInput(): ScreenResult {
        println("Available Models:")
        println("  [1] Daily Context")
        println("  [q] Exit Application")
        print("\nSelect Option > ")

        return when (readlnOrNull()?.trim()?.lowercase()) {
            "1" -> ScreenResult.NavigateTo(bioScreen)
            "q", "quit", "exit" -> ScreenResult.ExitApp
            else -> {
                println("[ERROR] Invalid choice.")
                ScreenResult.Stay
            }
        }
    }
}