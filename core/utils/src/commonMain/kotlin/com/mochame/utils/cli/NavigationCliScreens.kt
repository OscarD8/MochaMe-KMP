package com.mochame.utils.cli


sealed interface ScreenResult {
    data class NavigateTo(val destination: InteractiveScreen) : ScreenResult
    data object GoBack : ScreenResult
    data object Stay : ScreenResult
    data object ExitApp : ScreenResult
}

interface InteractiveScreen {
    val title: String
    suspend fun renderAndHandleInput(): ScreenResult
}

class CliScreenNavigator(
    rootScreen: InteractiveScreen
) {
    private val backStack = mutableListOf<InteractiveScreen>()

    init {
        backStack.add(rootScreen)
    }

    suspend fun start() {
        println("MochaMe System")

        while (backStack.isNotEmpty()) {
            val currentScreen = backStack.last()

            println("\n----------------------------------------")
            println("[SCREEN] " + currentScreen.title)
            println("----------------------------------------")

            when (val action = currentScreen.renderAndHandleInput()) {
                is ScreenResult.NavigateTo -> {
                    backStack.add(action.destination)
                }
                is ScreenResult.GoBack -> {
                    backStack.removeLastOrNull()
                    if (backStack.isEmpty()) {
                        println("Session ended.")
                    }
                }
                is ScreenResult.Stay -> {
                }
                is ScreenResult.ExitApp -> {
                    backStack.clear()
                    println("Session ended.")
                }
            }
        }
    }
}