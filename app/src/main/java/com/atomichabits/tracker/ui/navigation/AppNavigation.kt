package com.atomichabits.tracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.ui.addedit.AddEditHabitScreen
import com.atomichabits.tracker.ui.detail.HabitDetailScreen
import com.atomichabits.tracker.ui.habits.HabitsListScreen
import com.atomichabits.tracker.ui.history.HistoryScreen
import com.atomichabits.tracker.ui.home.HomeScreen
import com.atomichabits.tracker.ui.impulse.ImpulseScreen
import com.atomichabits.tracker.ui.scorecard.ScorecardScreen
import com.atomichabits.tracker.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADD_EDIT = "add_edit"
    const val DETAIL = "detail"
    const val IMPULSE = "impulse"
    const val SCORECARD = "scorecard"
    const val ARG_HABIT_ID = "habitId"
    const val ARG_INITIAL_NAME = "initialName"
    const val ARG_INITIAL_TYPE = "initialType"
    const val ARG_INITIAL_TRACKED = "initialTracked"

    fun addEdit(
        habitId: Long? = null,
        initialName: String? = null,
        initialQualityType: String? = null,
        initialTracked: Boolean? = null
    ): String {
        val encodedName = URLEncoder.encode(initialName ?: "", "UTF-8")
        val type = initialQualityType ?: ""
        val tracked = initialTracked?.toString() ?: ""
        return "$ADD_EDIT?${ARG_HABIT_ID}=${habitId ?: -1L}&${ARG_INITIAL_NAME}=$encodedName" +
            "&${ARG_INITIAL_TYPE}=$type&${ARG_INITIAL_TRACKED}=$tracked"
    }

    fun detail(habitId: Long) = "$DETAIL/$habitId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val emphasized: Boolean = false
)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Сегодня", Icons.Filled.Today),
    BottomTab(Routes.HABITS, "Привычки", Icons.Filled.Bolt),
    BottomTab(Routes.IMPULSE, "Позыв", Icons.Filled.Bolt, emphasized = true),
    BottomTab(Routes.HISTORY, "История", Icons.Filled.History),
    BottomTab(Routes.SETTINGS, "Я", Icons.Filled.Person)
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
                            icon = {
                                if (tab.emphasized) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(tab.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                            colors = if (tab.emphasized) {
                                NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                            } else {
                                NavigationBarItemDefaults.colors()
                            }
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

            composable(Routes.IMPULSE) {
                ImpulseScreen(app = app)
            }

            composable(Routes.HABITS) {
                HabitsListScreen(
                    app = app,
                    onOpenHabit = { id -> navController.navigate(Routes.detail(id)) },
                    onEditUntracked = { id -> navController.navigate(Routes.addEdit(id)) },
                    onAddHabit = { quality, tracked ->
                        navController.navigate(Routes.addEdit(initialQualityType = quality, initialTracked = tracked))
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(app = app, onOpenHabit = { id -> navController.navigate(Routes.detail(id)) })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    app = app,
                    onBack = null,
                    onOpenScorecard = { navController.navigate(Routes.SCORECARD) }
                )
            }

            composable(Routes.SCORECARD) {
                ScorecardScreen(app = app, onBack = { navController.popBackStack() })
            }

            composable(
                route = "${Routes.ADD_EDIT}?${Routes.ARG_HABIT_ID}={${Routes.ARG_HABIT_ID}}" +
                    "&${Routes.ARG_INITIAL_NAME}={${Routes.ARG_INITIAL_NAME}}" +
                    "&${Routes.ARG_INITIAL_TYPE}={${Routes.ARG_INITIAL_TYPE}}" +
                    "&${Routes.ARG_INITIAL_TRACKED}={${Routes.ARG_INITIAL_TRACKED}}",
                arguments = listOf(
                    navArgument(Routes.ARG_HABIT_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(Routes.ARG_INITIAL_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_INITIAL_TYPE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_INITIAL_TRACKED) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val habitId = entry.arguments?.getLong(Routes.ARG_HABIT_ID) ?: -1L
                val encodedName = entry.arguments?.getString(Routes.ARG_INITIAL_NAME) ?: ""
                val initialName = if (encodedName.isBlank()) null else URLDecoder.decode(encodedName, "UTF-8")
                val initialType = entry.arguments?.getString(Routes.ARG_INITIAL_TYPE)?.ifBlank { null }
                val initialTracked = entry.arguments?.getString(Routes.ARG_INITIAL_TRACKED)?.ifBlank { null }?.toBoolean()
                AddEditHabitScreen(
                    app = app,
                    habitId = if (habitId == -1L) null else habitId,
                    initialName = initialName,
                    initialQualityType = initialType,
                    initialTracked = initialTracked,
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
