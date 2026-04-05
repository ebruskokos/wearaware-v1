package com.wearaware.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.ui.components.*
import com.wearaware.app.ui.viewmodel.ScanState
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.BLE_PERMISSIONS.toList()
    ) { results ->
        if (PermissionUtils.allGranted(results)) viewModel.startScanning()
        else viewModel.setPermissionsRequired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WearAware") },
                actions = {
                    TextButton(onClick = onCaptureClick) {
                        Text(
                            text = "Compare",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (uiState.scanState == ScanState.SCANNING) {
                        TextButton(onClick = { viewModel.toggleFocusMode() }) {
                            Text(
                                text = if (uiState.focusMode) "All Devices" else "Focus Mode",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { viewModel.toggleDebugMode() }) {
                            Text(
                                text = if (uiState.debugMode) "Hide Debug" else "Debug",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Persistence alert banner
            val alert = uiState.activeAlert
            if (alert != null) {
                AlertBanner(
                    alert = alert,
                    onDismiss = { viewModel.dismissAlert() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Bluetooth off banner
            if (uiState.scanState == ScanState.BLUETOOTH_UNAVAILABLE) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bluetooth is turned off",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }) { Text("Turn on Bluetooth") }
                    }
                }
            }

            // Target match banner (while scanning)
            if (uiState.scanState == ScanState.SCANNING) {
                TargetMatchBanner(
                    bestMatch = uiState.bestMatch,
                    onDeviceClick = onDeviceClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Filter chips (while scanning)
            if (uiState.scanState == ScanState.SCANNING) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.activeFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Scan status header
            ScanStatusHeader(
                scanState = uiState.scanState,
                deviceCount = uiState.filteredDevices.size
            )

            // Focus mode label
            if (uiState.focusMode && uiState.scanState == ScanState.SCANNING) {
                Text(
                    text = "Sorted by match score for Wayfarer 00ZS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp)
                )
            }

            // Device list
            if (uiState.filteredDevices.isEmpty() && uiState.scanState == ScanState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMessage = if (uiState.activeFilter == ScanFilter.ALL)
                        "No nearby devices detected"
                    else
                        "No devices match \"${uiState.activeFilter.label}\" filter"
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.filteredDevices, key = { it.id }) { device ->
                        val matchResult = uiState.deviceMatchScores[device.id]
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) },
                            isTopCandidate = matchResult?.isTopCandidate == true
                        )
                    }
                }
            }

            // Scan control button
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    when {
                        uiState.scanState == ScanState.SCANNING -> viewModel.stopScanning()
                        permissionsState.allPermissionsGranted -> viewModel.startScanning()
                        else -> permissionsState.launchMultiplePermissionRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (uiState.scanState == ScanState.SCANNING) "Stop Scan" else "Start Scan")
            }

            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
