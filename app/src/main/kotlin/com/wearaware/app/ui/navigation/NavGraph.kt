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
import com.wearaware.app.ui.screens.LearningLogScreen
import com.wearaware.app.ui.screens.LearningSessionDetailScreen
import com.wearaware.app.ui.screens.PairAndLearnScreen
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
    object PairAndLearn : Screen("pair_and_learn")
    object LearningLog : Screen("learning_log")
    object LearningSessionDetail : Screen("learning_session/{sessionId}") {
        fun routeFor(sessionId: String) = "learning_session/$sessionId"
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
                },
                onPairAndLearnClick = {
                    navController.navigate(Screen.PairAndLearn.route)
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
        composable(Screen.PairAndLearn.route) {
            PairAndLearnScreen(
                onBack = { navController.popBackStack() },
                onViewLog = { navController.navigate(Screen.LearningLog.route) }
            )
        }
        composable(Screen.LearningLog.route) {
            LearningLogScreen(
                onBack = { navController.popBackStack() },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.LearningSessionDetail.routeFor(sessionId))
                }
            )
        }
        composable(Screen.LearningSessionDetail.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            LearningSessionDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
