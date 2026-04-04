package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.ui.components.SafeWording
import com.wearaware.app.ui.components.SignalBars
import com.wearaware.app.ui.components.VisibilityBadge
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.formatDuration
import com.wearaware.app.util.formatFullTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val device: ObservedDevice? = uiState.devices.firstOrNull { it.id == deviceId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.advertisedName ?: "Device Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Device no longer in session.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device identity
            Text(
                text = device.advertisedName ?: "No advertised name",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "MAC: ${device.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Signal + visibility
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(proximity = device.proximityLabel)
                Spacer(modifier = Modifier.width(8.dp))
                VisibilityBadge(state = device.visibilityState)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = device.proximityLabel.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Classification
            Text("Classification", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (device.classification.category == DeviceCategory.UNKNOWN_BLE_DEVICE)
                    SafeWording.UNKNOWN_DEVICE
                else
                    device.classification.displayLabel,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Category: ${device.classification.category}", style = MaterialTheme.typography.bodySmall)
            Text("Confidence: ${device.classification.confidence}", style = MaterialTheme.typography.bodySmall)
            device.classification.evaluationNotes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
            device.classification.matchedRuleId?.let {
                Text("Rule: $it (v${device.classification.ruleVersion})", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            // Timing
            Text("Session Info", style = MaterialTheme.typography.titleSmall)
            Text("First seen: ${device.firstSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
            Text("Last seen: ${device.lastSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
            Text("Duration nearby: ${device.seenDurationMs.formatDuration()}", style = MaterialTheme.typography.bodySmall)
            Text("Scan count: ${device.seenCount}", style = MaterialTheme.typography.bodySmall)

            // Persistence alert history
            device.persistenceAlert?.let { alert ->
                HorizontalDivider()
                Text("Alert History", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Alert triggered at ${alert.triggeredAt.formatFullTimestamp()}: ${SafeWording.DEVICE_REMAINED}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Disclaimer
            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = SafeWording.RECORDING_UNKNOWN,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
