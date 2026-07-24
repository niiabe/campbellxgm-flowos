package com.campbell.xgm.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campbell.xgm.ui.screens.DashboardScreen
import com.campbell.xgm.ui.screens.SettingsScreen
import com.campbell.xgm.ui.screens.AboutScreen
import com.campbell.xgm.ui.screens.AdbSetupScreen
import com.campbell.xgm.ui.screens.PermissionsScreen
import com.campbell.xgm.ui.screens.OnboardingScreen
import com.campbell.xgm.ui.screens.UpdateScreen
import com.campbell.xgm.ui.screens.UpdateUiState
import com.campbell.xgm.ui.screens.UpdateViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object About : Screen("about")
    object AdbSetup : Screen("adb_setup")
    object Permissions : Screen("permissions")
    object Onboarding : Screen("onboarding")
    object Update : Screen("update")
}

@Composable
fun AppNavigation(startDestination: String = Screen.Dashboard.route) {
    val navController = rememberNavController()
    val updateViewModel: UpdateViewModel = viewModel()
    var navigatedToUpdate by remember { mutableStateOf(false) }

    // Check for an available update in the background and surface it automatically.
    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdate(autoPrompt = true)
    }

    val updateState by updateViewModel.state.collectAsState()
    LaunchedEffect(updateState) {
        if (updateState is UpdateUiState.Available && !navigatedToUpdate) {
            navigatedToUpdate = true
            navController.navigate("update/true")
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
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
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToPermissions = {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdbSetup = { navController.navigate(Screen.AdbSetup.route) },
                onNavigateToUpdate = { navController.navigate("update/false") }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.AdbSetup.route) {
            AdbSetupScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = "update/{autoPrompt}",
            arguments = listOf(
                androidx.navigation.navArgument("autoPrompt") {
                    defaultValue = false
                    type = androidx.navigation.NavType.BoolType
                }
            )
        ) { backStackEntry ->
            val autoPrompt = backStackEntry.arguments?.getBoolean("autoPrompt") ?: false
            UpdateScreen(
                autoPrompt = autoPrompt,
                viewModel = updateViewModel,
                onDismiss = {
                    val state = updateViewModel.state.value
                    if (state is UpdateUiState.Available) {
                        updateViewModel.skip(state.release.tag)
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}
