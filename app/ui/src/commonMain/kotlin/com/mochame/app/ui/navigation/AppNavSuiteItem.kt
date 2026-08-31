package com.mochame.app.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Dashboard : Destination

    @Serializable
    data class DailyContext(val epochDay: Long? = null) : Destination
}

sealed class AppNavSuiteItem() {
    abstract fun isSelected(currentDestination: NavDestination?): Boolean

    data object Dashboard : AppNavSuiteItem() {
        override fun isSelected(currentDestination: NavDestination?): Boolean =
            currentDestination?.hasRoute<Destination.Dashboard>() == true
    }

    data object Bio : AppNavSuiteItem(
    ) {
        override fun isSelected(currentDestination: NavDestination?): Boolean =
            currentDestination?.hasRoute<Destination.DailyContext>() == true
    }

    companion object {
        val entries: List<AppNavSuiteItem> = listOf(Dashboard, Bio)
    }
}