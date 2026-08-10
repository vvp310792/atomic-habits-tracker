package com.atomichabits.tracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.ui.addedit.AddEditHabitScreen
import com.atomichabits.tracker.ui.detail.HabitDetailScreen
import com.atomichabits.tracker.ui.home.HomeScreen
import com.atomichabits.tracker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADD_EDIT = "add_edit"
    const val DETAIL = "detail"
    const val ARG_HABIT_ID = "habitId"

    fun addEdit(habitId: Long? = null) = "$ADD_EDIT?${ARG_HABIT_ID}=${habitId ?: -1L}"
    fun detail(habitId: Long) = "$DETAIL/$habitId"
}

@Composable
fun AppNavigation(app: HabitTrackerApp) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                app = app,
                onAddHabit = { navController.navigate(Routes.addEdit()) },
                onOpenHabit = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = "${Routes.ADD_EDIT}?${Routes.ARG_HABIT_ID}={${Routes.ARG_HABIT_ID}}",
            arguments = listOf(navArgument(Routes.ARG_HABIT_ID) {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getLong(Routes.ARG_HABIT_ID) ?: -1L
            AddEditHabitScreen(
                app = app,
                habitId = if (habitId == -1L) null else habitId,
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.DETAIL}/{${Routes.ARG_HABIT_ID}}",
            arguments = listOf(navArgument(Routes.ARG_HABIT_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getLong(Routes.ARG_HABIT_ID) ?: return@composable
            HabitDetailScreen(
                app = app,
                habitId = habitId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.addEdit(habitId)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(app = app, onBack = { navController.popBackStack() })
        }
    }
}
