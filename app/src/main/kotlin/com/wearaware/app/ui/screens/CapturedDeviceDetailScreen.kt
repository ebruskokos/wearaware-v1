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
import com.wearaware.app.domain.model.CaptureObservationLog
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.model.CapturedDevice
import com.wearaware.app.domain.model.CompareConfidence
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.LearnedConfidence
import com.wearaware.app.ui.viewmodel.CaptureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedDeviceDetailScreen(
    fingerprintId: String,
    onBack: () -> Unit,
    viewModel: CaptureViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Search target then baseline for the device
    val device: CapturedDevice? = uiState.target?.devices?.firstOrNull { it.fingerprintId == fingerprintId }
        ?: uiState.baseline?.devices?.firstOrNull { it.fingerprintId == fingerprintId }
    val compareResult: CompareMatchResult? = uiState.compareResults.firstOrNull {
        it.capturedDevice.fingerprintId == fingerprintId
    }
    val captureType = if (uiState.target?.devices?.any { it.fingerprintId == fingerprintId } == true)
        CaptureType.TARGET else CaptureType.BASELINE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.advertisedName ?: "Captured Device") },
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
            ) { Text("Device not found in captured sessions.") }
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
                device.advertisedName ?: "No advertised name",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Capture: ${captureType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Fingerprint ID: ${device.fingerprintId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            device.macAddress?.let {
                Text("MAC: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Learn this device button
            val learnedSignature = uiState.learnedSignature
            val alreadySaved = learnedSignature?.fingerprintId == device.fingerprintId
            Button(
                onClick = { viewModel.learnDevice(device.fingerprintId) },
                enabled = !alreadySaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (alreadySaved) "Already saved as learned device"
                    else "Learn this device — This is my glasses"
                )
            }

            HorizontalDivider()

            // --- Best observed evidence during capture ---
            Text("Best Observed Evidence During Capture", style = MaterialTheme.typography.titleSmall)

            if (device.observationLog != null) {
                CaptureEvidenceSection(device = device, log = device.observationLog)
            } else {
                Text(
                    "Observation log not available — device was loaded from a previous session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // --- Compare result ---
            if (compareResult != null) {
                Text("Compare Result", style = MaterialTheme.typography.titleSmall)
                val confidenceColor = when (compareResult.confidence) {
                    CompareConfidence.HIGH -> MaterialTheme.colorScheme.primary
                    CompareConfidence.MEDIUM -> MaterialTheme.colorScheme.secondary
                    CompareConfidence.LOW -> MaterialTheme.colorScheme.tertiary
                    CompareConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    "${compareResult.confidence.name} — score ${compareResult.score}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = confidenceColor
                )
                if (compareResult.comparisonSignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Why this device:", style = MaterialTheme.typography.labelSmall)
                    compareResult.comparisonSignals.forEach { signal ->
                        Text(
                            "  • $signal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val rssiText = if (compareResult.baselineAverageRssi != null) {
                    val deltaStr = compareResult.rssiDelta?.let { if (it >= 0) "+$it" else "$it" } ?: "n/a"
                    "RSSI: baseline ${compareResult.baselineAverageRssi} dBm → target ${device.averageRssi} dBm  Δ $deltaStr dBm"
                } else {
                    "RSSI: baseline n/a → target ${device.averageRssi} dBm"
                }
                Text(rssiText, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
            }

            // --- Capture stats ---
            Text("Capture Statistics", style = MaterialTheme.typography.titleSmall)
            Text("Seen count: ${device.seenCount}", style = MaterialTheme.typography.bodySmall)
            Text("Peak RSSI: ${device.peakRssi} dBm", style = MaterialTheme.typography.bodySmall)
            Text("Average RSSI: ${device.averageRssi} dBm", style = MaterialTheme.typography.bodySmall)
            Text(
                "Visible at stop: ${if (device.visibleAtStop) "Yes — still advertising when capture ended" else "No"}",
                style = MaterialTheme.typography.bodySmall
            )

            // --- Classification ---
            HorizontalDivider()
            Text("Classification", style = MaterialTheme.typography.titleSmall)
            Text(
                device.category.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium
            )
            if (device.companyNames.isNotEmpty()) {
                Text(
                    "Manufacturer: ${device.companyNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val idHex = device.manufacturerIds.map { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            if (idHex.isNotEmpty()) {
                Text("Manufacturer IDs: ${idHex.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
            device.manufacturerDataSummary?.let {
                Text("Manufacturer data: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (device.serviceUuids.isNotEmpty()) {
                Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                device.serviceUuids.forEach { uuid ->
                    Text(
                        "  $uuid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Target match ---
            if (device.targetMatchScore != null && device.targetMatchScore > 0) {
                HorizontalDivider()
                Text("Target Match (Wayfarer 00ZS)", style = MaterialTheme.typography.titleSmall)
                Text("Score: ${device.targetMatchScore}", style = MaterialTheme.typography.bodySmall)
                if (device.targetMatchSignals.isNotEmpty()) {
                    device.targetMatchSignals.forEach { signal ->
                        Text(
                            "  • $signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Learned signature match debug section
            HorizontalDivider()
            Text("Learned Signature Match", style = MaterialTheme.typography.titleSmall)
            val learnedSignatureDebug = uiState.learnedSignature
            if (learnedSignatureDebug == null) {
                Text(
                    "No learned signature saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Learned signature: ${learnedSignatureDebug.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
                val learnedMatch = uiState.learnedMatchResults[fingerprintId]
                if (learnedMatch == null) {
                    Text(
                        "Match: not computed (run compare first)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val confidenceColor = when (learnedMatch.confidence) {
                        LearnedConfidence.STRONG -> MaterialTheme.colorScheme.primary
                        LearnedConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
                        LearnedConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        "Match confidence: ${learnedMatch.confidence.name} (score ${learnedMatch.score})",
                        style = MaterialTheme.typography.bodySmall,
                        color = confidenceColor
                    )
                    Text(
                        "Label override active: ${if (learnedMatch.labelOverrideActive) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (learnedMatch.matchedSignals.isNotEmpty()) {
                        Text("Matched signals:", style = MaterialTheme.typography.labelSmall)
                        learnedMatch.matchedSignals.forEach { signal ->
                            Text(
                                "  • $signal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "No signals matched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureEvidenceSection(device: CapturedDevice, log: CaptureObservationLog) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // Names
        if (log.observedNames.isEmpty()) {
            Text(
                "Advertised name: never observed during capture",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (log.observedNames.size == 1) {
            Text(
                "Advertised name: ${log.observedNames.first()} (stable)",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Advertised names seen (${log.observedNames.size} distinct):",
                style = MaterialTheme.typography.bodySmall
            )
            log.observedNames.forEach { name ->
                Text(
                    "  • $name",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Manufacturer IDs
        if (log.bestManufacturerIds.isEmpty()) {
            Text(
                "Manufacturer IDs: none observed during capture",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val idsHex = log.bestManufacturerIds.map { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            Text(
                "Best manufacturer IDs: ${idsHex.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = if (log.bestManufacturerIds.contains(0x0075))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        // Manufacturer data
        log.bestManufacturerDataSummary?.let {
            Text("Best manufacturer data: $it", style = MaterialTheme.typography.bodySmall)
        } ?: Text(
            "Manufacturer data: none observed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Classification history
        if (log.classificationHistory.size == 1) {
            Text(
                "Classification: ${log.classificationHistory.first().name.replace('_', ' ')} (stable)",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Classification changed during capture:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            log.classificationHistory.forEachIndexed { i, cat ->
                val label = if (i == 0) "Initial" else "→ tick $i"
                Text(
                    "  $label: ${cat.name.replace('_', ' ')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Target match score history
        if (log.targetMatchScoreHistory.isEmpty()) {
            Text(
                "Target match: no match score recorded during capture",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (log.targetMatchScoreHistory.size == 1) {
            Text(
                "Target match score: ${log.targetMatchScoreHistory.first()} (first and only observation)",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Target match score improved ${log.targetMatchScoreHistory.size} times: ${log.targetMatchScoreHistory.joinToString(" → ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Peak RSSI
        Text(
            "Peak RSSI during capture: ${device.peakRssi} dBm",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
