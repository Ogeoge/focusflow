package com.focusflow.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusflow.app.ui.screens.history.HistoryScreen
import com.focusflow.app.ui.screens.history.HistoryViewModel
import com.focusflow.app.ui.screens.settings.SettingsScreen
import com.focusflow.app.ui.screens.settings.SettingsViewModel
import com.focusflow.app.ui.screens.tasks.TasksScreen
import com.focusflow.app.ui.screens.tasks.TasksViewModel
import com.focusflow.app.ui.screens.timer.TimerScreen
import com.focusflow.app.ui.screens.timer.TimerViewModel

object Routes {
    const val TIMER = "timer"
    const val TASKS = "tasks"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class BottomDest(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun AppNav(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val destinations = listOf(
        BottomDest(
            route = Routes.TIMER,
            label = "Timer",
            icon = { Icon(imageVector = Icons.Default.Timer, contentDescription = null) },
        ),
        BottomDest(
            route = Routes.TASKS,
            label = "Tasks",
            icon = { Icon(imageVector = Icons.Default.ViewList, contentDescription = null) },
        ),
        BottomDest(
            route = Routes.HISTORY,
            label = "History",
            icon = { Icon(imageVector = Icons.Default.History, contentDescription = null) },
        ),
        BottomDest(
            route = Routes.SETTINGS,
            label = "Settings",
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
        ),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == dest.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = dest.icon,
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIMER,
            modifier = Modifier,
        ) {
            composable(Routes.TIMER) {
                val vm: TimerViewModel = viewModel()
                TimerScreen(viewModel = vm)
            }
            composable(Routes.TASKS) {
                val vm: TasksViewModel = viewModel()
                TasksScreen(viewModel = vm)
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel()
                HistoryScreen(viewModel = vm)
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = vm)
            }
        }
    }
}
