package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.MatchConfidence
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
    val matchResult = uiState.deviceMatchScores[deviceId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.advertisedName ?: "Device Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (device == null) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Device no longer in session.") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Identity ---
            Text(
                text = device.advertisedName ?: "No advertised name",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "MAC: ${device.macAddress ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Fingerprint ID: ${device.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // --- Manufacturer / Company ---
            Text("Manufacturer", style = MaterialTheme.typography.titleSmall)
            if (device.companyNames.isNotEmpty()) {
                device.companyNames.forEach { name ->
                    Text(text = "• $name", style = MaterialTheme.typography.bodySmall)
                }
                device.fingerprint?.manufacturerIds?.forEach { id ->
                    Text(
                        text = "  Company ID: 0x${id.toString(16).uppercase().padStart(4, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "No manufacturer data in advertisement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // --- Signal + Visibility ---
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
            Text(
                text = "Averaged RSSI: ${device.averagedRssi} dBm  •  Raw: ${device.rawRssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            // --- Classification ---
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

            // --- Session Info ---
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
                    text = "Alert at ${alert.triggeredAt.formatFullTimestamp()}: ${SafeWording.DEVICE_REMAINED}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // --- Target Match Analysis ---
            Text("Target Match Analysis", style = MaterialTheme.typography.titleSmall)
            Text("Target: Wayfarer 00ZS", style = MaterialTheme.typography.bodySmall)
            if (matchResult == null || matchResult.score == 0) {
                Text(
                    text = "No match signals found for this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Score: ${matchResult.score} • Confidence: ${matchResult.confidence.name}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (matchResult.isTopCandidate &&
                    (matchResult.confidence == MatchConfidence.HIGH ||
                        matchResult.confidence == MatchConfidence.MEDIUM)) {
                    Text(
                        text = "★ Best current candidate for Wayfarer 00ZS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (matchResult.matchedSignals.isNotEmpty()) {
                    Text("Matched signals:", style = MaterialTheme.typography.labelSmall)
                    matchResult.matchedSignals.forEach { signal ->
                        Text(
                            text = "  • $signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Debug View (shown when debug mode is on) ---
            if (uiState.debugMode) {
                HorizontalDivider()
                Text(
                    "Debug Info",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Fingerprint ID: ${device.id}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "MAC address: ${device.macAddress ?: "n/a"}",
                    style = MaterialTheme.typography.labelSmall
                )
                val fp = device.fingerprint
                if (fp != null) {
                    Text(
                        text = "Manufacturer IDs: ${fp.manufacturerIds.map { "0x${it.toString(16).uppercase()}" }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    fp.manufacturerDataHex.forEach { (id, hex) ->
                        Text(
                            text = "  0x${id.toString(16).uppercase()}: $hex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (fp.serviceUuids.isNotEmpty()) {
                        Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                        fp.serviceUuids.forEach { uuid ->
                            Text(
                                text = "  $uuid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    fp.txPower?.let {
                        Text("TX Power: $it dBm", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = "Classification rule: ${device.classification.matchedRuleId ?: "none"}",
                    style = MaterialTheme.typography.labelSmall
                )
                matchResult?.let {
                    Text(
                        text = "Target match score: ${it.score} (${it.confidence.name})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            HorizontalDivider()

            // --- Disclaimers ---
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
