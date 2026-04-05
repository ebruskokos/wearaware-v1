package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.model.CompareConfidence
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.ui.viewmodel.CaptureState
import com.wearaware.app.ui.viewmodel.CaptureViewModel
import com.wearaware.app.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onViewDeviceDetail: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Target Capture & Compare") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Baseline
            CaptureSection(
                title = "Step 1: Baseline (glasses OFF)",
                subtitle = "Scan the environment before powering on your target device.",
                captureState = uiState.baselineCaptureState,
                deviceCount = uiState.baseline?.devices?.size,
                durationMs = uiState.baseline?.let { it.stoppedAt - it.startedAt },
                liveCount = if (uiState.baselineCaptureState == CaptureState.CAPTURING)
                    uiState.liveAccumulatedCount else null,
                onStart = { viewModel.startCapture(CaptureType.BASELINE) },
                onStop = { viewModel.stopCapture(CaptureType.BASELINE) },
                startEnabled = uiState.targetCaptureState != CaptureState.CAPTURING,
                otherCaptureActive = uiState.targetCaptureState == CaptureState.CAPTURING
            )

            HorizontalDivider()

            // Section 2: Target
            CaptureSection(
                title = "Step 2: Target (glasses ON and nearby)",
                subtitle = "Power on your target device, then start this capture.",
                captureState = uiState.targetCaptureState,
                deviceCount = uiState.target?.devices?.size,
                durationMs = uiState.target?.let { it.stoppedAt - it.startedAt },
                liveCount = if (uiState.targetCaptureState == CaptureState.CAPTURING)
                    uiState.liveAccumulatedCount else null,
                onStart = { viewModel.startCapture(CaptureType.TARGET) },
                onStop = { viewModel.stopCapture(CaptureType.TARGET) },
                startEnabled = uiState.baselineCaptureState != CaptureState.CAPTURING,
                otherCaptureActive = uiState.baselineCaptureState == CaptureState.CAPTURING
            )

            // Section 3: Compare results
            if (uiState.targetCaptureState == CaptureState.DONE) {
                HorizontalDivider()
                CompareResultsSection(
                    results = uiState.compareResults,
                    hasBaseline = uiState.baseline != null,
                    onViewDevice = onViewDeviceDetail
                )
            }
        }
    }
}

@Composable
private fun CaptureSection(
    title: String,
    subtitle: String,
    captureState: CaptureState,
    deviceCount: Int?,
    durationMs: Long?,
    liveCount: Int?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    startEnabled: Boolean,
    otherCaptureActive: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val chipLabel = when (captureState) {
            CaptureState.IDLE -> "IDLE"
            CaptureState.CAPTURING -> "● CAPTURING${liveCount?.let { " — $it devices" } ?: ""}"
            CaptureState.DONE -> "✓ DONE — ${deviceCount ?: 0} devices"
        }
        SuggestionChip(onClick = {}, label = {
            Text(chipLabel, style = MaterialTheme.typography.labelSmall)
        })

        if (captureState == CaptureState.CAPTURING) {
            Text(
                "Scan for at least 30 seconds for best results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (captureState == CaptureState.DONE && durationMs != null) {
            Text(
                "Captured ${deviceCount ?: 0} devices over ${durationMs.formatDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (otherCaptureActive && captureState != CaptureState.CAPTURING) {
            Text(
                "Stop the active capture before starting a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = if (captureState == CaptureState.CAPTURING) onStop else onStart,
            enabled = captureState == CaptureState.CAPTURING || startEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (captureState) {
                    CaptureState.IDLE -> "Start Capture"
                    CaptureState.CAPTURING -> "Stop Capture"
                    CaptureState.DONE -> "Re-capture"
                }
            )
        }
    }
}

@Composable
private fun CompareResultsSection(
    results: List<CompareMatchResult>,
    hasBaseline: Boolean,
    onViewDevice: (String) -> Unit
) {
    // Snapshot to prevent list mutation during composition
    val safeResults = results.toList()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val headerText = if (safeResults.isEmpty()) {
            "Compare Results"
        } else {
            val count = safeResults.size
            "Compare Results — $count candidate${if (count == 1) "" else "s"}"
        }
        Text(headerText, style = MaterialTheme.typography.titleSmall)

        if (safeResults.isNotEmpty() && safeResults.all { it.confidence == CompareConfidence.LOW }) {
            Text(
                "All results are LOW confidence — only weak differential evidence found. " +
                    "Try a longer capture or move closer to your target device before powering it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!hasBaseline) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "ℹ No baseline was captured. Results show target-only evidence without differential comparison — treat with lower confidence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Use if/else instead of early return — early return inside a @Composable lambda
        // causes startGroup/endGroup mismatches in Compose's slot table, crashing with
        // IndexOutOfBoundsException: Index -1 out of bounds (Stack.pop on empty stack).
        if (safeResults.isEmpty()) {
            Text(
                "No strong differential match found yet.\nTry a longer capture or move closer to your target device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            safeResults.forEachIndexed { index, result ->
                // key() ensures Compose maps each card to its device fingerprint, not its
                // list position — prevents slot reuse when result count changes between
                // recompositions (e.g. after a re-capture produces a different result set).
                key(result.capturedDevice.fingerprintId) {
                    val isTopCandidate = index == 0 &&
                        (result.confidence == CompareConfidence.HIGH ||
                            result.confidence == CompareConfidence.MEDIUM)
                    CompareResultCard(
                        result = result,
                        isTopCandidate = isTopCandidate,
                        onViewDevice = onViewDevice
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareResultCard(
    result: CompareMatchResult,
    isTopCandidate: Boolean,
    onViewDevice: (String) -> Unit
) {
    val device = result.capturedDevice
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTopCandidate)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isTopCandidate) {
                Text(
                    "★ Most likely new candidate after target device was powered on",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val confidenceColor = when (result.confidence) {
                CompareConfidence.HIGH -> MaterialTheme.colorScheme.primary
                CompareConfidence.MEDIUM -> MaterialTheme.colorScheme.secondary
                CompareConfidence.LOW -> MaterialTheme.colorScheme.tertiary
                CompareConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                result.confidence.name,
                style = MaterialTheme.typography.labelSmall,
                color = confidenceColor
            )

            val displayName = device.advertisedName
                ?: device.companyNames.firstOrNull()?.let { "$it device" }
                ?: "Unknown BLE Device"
            Text(displayName, style = MaterialTheme.typography.bodyMedium)

            if (device.companyNames.isNotEmpty()) {
                val idHex = device.manufacturerIds.firstOrNull()
                    ?.let { "  0x${it.toString(16).uppercase().padStart(4, '0')}" } ?: ""
                Text(
                    "Manufacturer: ${device.companyNames.joinToString(", ")}$idHex",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Seen in baseline: ${if (result.seenInBaseline) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Seen in target: Yes", style = MaterialTheme.typography.bodySmall)
            }

            val rssiText = if (result.baselineAverageRssi != null) {
                val deltaStr = result.rssiDelta?.let { if (it >= 0) "+$it" else "$it" } ?: "n/a"
                "RSSI: baseline ${result.baselineAverageRssi} dBm → target ${device.averageRssi} dBm  Δ $deltaStr dBm"
            } else {
                "RSSI: baseline n/a → target ${device.averageRssi} dBm"
            }
            Text(rssiText, style = MaterialTheme.typography.bodySmall)

            if (result.comparisonSignals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Why this device:", style = MaterialTheme.typography.labelSmall)
                result.comparisonSignals.forEach { signal ->
                    key(signal) {
                        Text(
                            "  • $signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            device.manufacturerDataSummary?.let {
                Text(
                    "Manufacturer data: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (device.serviceUuids.isNotEmpty()) {
                Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                device.serviceUuids.take(3).forEach { uuid ->
                    Text(
                        "  $uuid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!result.hasBaseline) {
                Text(
                    "Note: no baseline — treat with lower confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { onViewDevice(device.fingerprintId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Device Details")
            }
        }
    }
}
