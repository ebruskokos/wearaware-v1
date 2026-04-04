package com.wearaware.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wearaware.app.ui.screens.DeviceDetailScreen
import com.wearaware.app.ui.screens.ScanScreen
import com.wearaware.app.ui.screens.SessionLogScreen

sealed class Screen(val route: String) {
    object Scan : Screen("scan")
    object DeviceDetail : Screen("device/{deviceId}") {
        fun routeFor(deviceId: String) = "device/$deviceId"
    }
    object SessionLog : Screen("session_log")
}

@Composable
fun WearAwareNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Scan.route) {
        composable(Screen.Scan.route) {
            ScanScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.routeFor(deviceId))
                }
            )
        }
        composable(Screen.DeviceDetail.route) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.SessionLog.route) {
            SessionLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
