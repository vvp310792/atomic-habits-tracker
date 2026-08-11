package com.atomichabits.tracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.ui.addedit.AddEditHabitScreen
import com.atomichabits.tracker.ui.anchors.AnchorLibraryScreen
import com.atomichabits.tracker.ui.detail.HabitDetailScreen
import com.atomichabits.tracker.ui.habits.HabitsListScreen
import com.atomichabits.tracker.ui.history.HistoryScreen
import com.atomichabits.tracker.ui.home.HomeScreen
import com.atomichabits.tracker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADD_EDIT = "add_edit"
    const val DETAIL = "detail"
    const val ANCHORS = "anchors"
    const val ARG_HABIT_ID = "habitId"

    fun addEdit(habitId: Long? = null) = "$ADD_EDIT?${ARG_HABIT_ID}=${habitId ?: -1L}"
    fun detail(habitId: Long) = "$DETAIL/$habitId"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Сегодня", Icons.Filled.Today),
    BottomTab(Routes.HABITS, "Привычки", Icons.Filled.Bolt),
    BottomTab(Routes.HISTORY, "История", Icons.Filled.History),
    BottomTab(Routes.SETTINGS, "Настройки", Icons.Filled.Settings)
)

@Composable
fun AppNavigation(app: HabitTrackerApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BOTTOM_TABS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {

            composable(Routes.HOME) {
                HomeScreen(
                    app = app,
                    onAddHabit = { navController.navigate(Routes.addEdit()) },
                    onOpenHabit = { id -> navController.navigate(Routes.detail(id)) }
                )
            }

            composable(Routes.HABITS) {
                HabitsListScreen(
                    app = app,
                    onAddHabit = { navController.navigate(Routes.addEdit()) },
                    onOpenHabit = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenAnchors = { navController.navigate(Routes.ANCHORS) }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(app = app, onOpenHabit = { id -> navController.navigate(Routes.detail(id)) })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(app = app, onBack = null)
            }

            composable(Routes.ANCHORS) {
                AnchorLibraryScreen(app = app, onBack = { navController.popBackStack() })
            }

            composable(
                route = "${Routes.ADD_EDIT}?${Routes.ARG_HABIT_ID}={${Routes.ARG_HABIT_ID}}",
                arguments = listOf(navArgument(Routes.ARG_HABIT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { entry ->
                val habitId = entry.arguments?.getLong(Routes.ARG_HABIT_ID) ?: -1L
                AddEditHabitScreen(
                    app = app,
                    habitId = if (habitId == -1L) null else habitId,
                    onDone = { navController.popBackStack() }
                )
            }

            composable(
                route = "${Routes.DETAIL}/{${Routes.ARG_HABIT_ID}}",
                arguments = listOf(navArgument(Routes.ARG_HABIT_ID) { type = NavType.LongType })
            ) { entry ->
                val habitId = entry.arguments?.getLong(Routes.ARG_HABIT_ID) ?: return@composable
                HabitDetailScreen(
                    app = app,
                    habitId = habitId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.addEdit(habitId)) }
                )
            }
        }
    }
}
