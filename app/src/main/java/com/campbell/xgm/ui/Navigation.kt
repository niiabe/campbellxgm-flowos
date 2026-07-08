package com.campbell.xgm.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campbell.xgm.ui.screens.DashboardScreen
import com.campbell.xgm.ui.screens.SettingsScreen
import com.campbell.xgm.ui.screens.AboutScreen
import com.campbell.xgm.ui.screens.AdbSetupScreen
import com.campbell.xgm.ui.screens.PermissionsScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object About : Screen("about")
    object AdbSetup : Screen("adb_setup")
    object Permissions : Screen("permissions")
}

@Composable
fun AppNavigation(startDestination: String = Screen.Dashboard.route) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdbSetup = { navController.navigate(Screen.AdbSetup.route) }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.AdbSetup.route) {
            AdbSetupScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
