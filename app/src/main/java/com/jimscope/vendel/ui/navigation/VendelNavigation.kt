package com.jimscope.vendel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jimscope.vendel.ui.log.MessageLogScreen
import com.jimscope.vendel.ui.onboarding.OnboardingScreen
import com.jimscope.vendel.ui.settings.SettingsScreen
import com.jimscope.vendel.ui.setup.SetupScreen
import com.jimscope.vendel.ui.status.StatusScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Setup : Screen("setup")
    data object Status : Screen("status")
    data object Log : Screen("log")
    data object Settings : Screen("settings")
}

@Composable
fun VendelNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onOnboardingComplete: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Status.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Status.route) {
            StatusScreen()
        }
        composable(Screen.Log.route) {
            MessageLogScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onDisconnect = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
