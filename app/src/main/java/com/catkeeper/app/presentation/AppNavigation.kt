package com.catkeeper.app.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.catkeeper.app.data.SettingsRepository
import com.catkeeper.app.data.database.StatsRepository
import com.catkeeper.app.presentation.screens.HomeScreen
import com.catkeeper.app.presentation.screens.SettingsScreen

@Composable
fun AppNavigation(
    onRequestNotificationPermission: () -> Unit,
    settingsRepository: SettingsRepository,
    statsRepository: StatsRepository
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onRequestNotificationPermission = onRequestNotificationPermission,
                onNavigateToSettings = { navController.navigate("settings") },
                settingsRepository = settingsRepository,
                statsRepository = statsRepository
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                settingsRepository = settingsRepository
            )
        }
    }
}
