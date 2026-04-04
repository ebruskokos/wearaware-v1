package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.ui.components.*
import com.wearaware.app.ui.viewmodel.ScanState
import com.wearaware.app.ui.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("WearAware") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Alert banner
            val alert = uiState.activeAlert
            if (alert != null) {
                AlertBanner(
                    alert = alert,
                    onDismiss = { viewModel.dismissAlert() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Scan status header
            ScanStatusHeader(
                scanState = uiState.scanState,
                deviceCount = uiState.devices.size
            )

            // Device list
            if (uiState.devices.isEmpty() && uiState.scanState == ScanState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No nearby devices detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.devices,
                        key = { it.id }
                    ) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) }
                        )
                    }
                }
            }

            // Scan control button
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (uiState.scanState == ScanState.SCANNING) viewModel.stopScanning()
                    else viewModel.startScanning()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (uiState.scanState == ScanState.SCANNING) "Stop Scan" else "Start Scan")
            }

            // Disclaimer
            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
    }
}
