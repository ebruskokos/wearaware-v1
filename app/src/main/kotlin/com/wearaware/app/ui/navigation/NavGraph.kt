package com.wearaware.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wearaware.app.ui.screens.CaptureScreen
import com.wearaware.app.ui.screens.CapturedDeviceDetailScreen
import com.wearaware.app.ui.screens.DeviceDetailScreen
import com.wearaware.app.ui.screens.ScanScreen
import com.wearaware.app.ui.screens.SessionLogScreen
import com.wearaware.app.ui.viewmodel.CaptureViewModel
import com.wearaware.app.ui.viewmodel.ScanViewModel

sealed class Screen(val route: String) {
    object Scan : Screen("scan")
    object DeviceDetail : Screen("device/{deviceId}") {
        fun routeFor(deviceId: String) = "device/$deviceId"
    }
    object SessionLog : Screen("session_log")
    object Capture : Screen("capture")
    object CaptureDeviceDetail : Screen("capture_device/{fingerprintId}") {
        fun routeFor(fingerprintId: String) = "capture_device/$fingerprintId"
    }
}

@Composable
fun WearAwareNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Scan.route) {
        composable(Screen.Scan.route) {
            ScanScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.routeFor(deviceId))
                },
                onCaptureClick = {
                    navController.navigate(Screen.Capture.route)
                }
            )
        }
        composable(Screen.DeviceDetail.route) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            // Share ScanViewModel with ScanScreen so device data is still available
            val scanEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Scan.route)
            }
            val viewModel: ScanViewModel = hiltViewModel(scanEntry)
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable(Screen.SessionLog.route) {
            SessionLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Capture.route) {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onViewDeviceDetail = { fingerprintId ->
                    navController.navigate(Screen.CaptureDeviceDetail.routeFor(fingerprintId))
                }
            )
        }
        composable(Screen.CaptureDeviceDetail.route) { backStackEntry ->
            val fingerprintId = backStackEntry.arguments?.getString("fingerprintId") ?: return@composable
            // Share CaptureViewModel with CaptureScreen so session data is still available
            val captureEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Capture.route)
            }
            val viewModel: CaptureViewModel = hiltViewModel(captureEntry)
            CapturedDeviceDetailScreen(
                fingerprintId = fingerprintId,
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
    }
}
