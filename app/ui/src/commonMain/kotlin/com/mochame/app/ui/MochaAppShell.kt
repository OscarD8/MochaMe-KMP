package com.mochame.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mochame.app.ui.navigation.AppNavSuiteItem
import com.mochame.app.ui.navigation.Destination
import com.mochame.app.ui.navigation.navigateToSuiteItem
import com.mochame.bio.ui.DailyContextRoute
import com.mochame.ui.screens.DashboardScreen
import com.mochame.utils.interfaces.MochaTimeUtils
import org.koin.compose.koinInject

@Composable
fun MochaComposeAppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val timeProvider: MochaTimeUtils = koinInject()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppNavSuiteItem.entries.forEach { item ->
                item(
                    selected = item.isSelected(currentDestination),
                    onClick = {
                        val destination = when (item) {
                            AppNavSuiteItem.Bio -> Destination.DailyContext(timeProvider.getMochaDay())
                            else -> item.defaultDestination
                        }
                        navController.navigateToSuiteItem(destination)
                    },
                    icon = { Text(item.label.take(1)) },
                    label = { Text(item.label) }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<Destination.Dashboard> {
                DashboardScreen(
                    onNavigateToBio = { targetDay ->
                        navController.navigate(Destination.DailyContext(targetDay))
                    }
                )
            }

            composable<Destination.DailyContext> { backStackEntry ->
                val route = backStackEntry.toRoute<Destination.DailyContext>()
                val resolvedDay = route.epochDay ?: timeProvider.getMochaDay()

                DailyContextRoute(epochDay = resolvedDay)
            }
        }
    }
}