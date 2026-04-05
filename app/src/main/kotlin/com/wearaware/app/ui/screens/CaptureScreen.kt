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
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.model.CompareConfidence
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.learnSaveConfirmation) {
        val msg = uiState.learnSaveConfirmation
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                captureStartedAt = uiState.captureStartedAt,
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
                captureStartedAt = uiState.captureStartedAt,
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
                    onViewDevice = onViewDeviceDetail,
                    learnedSignature = uiState.knownTargetSignature,
                    learnedMatchResults = uiState.learnedMatchResults,
                    onLearnDevice = { viewModel.learnDevice(it) },
                    onClearLearnedDevice = { viewModel.clearLearnedDevice() }
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
    captureStartedAt: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    startEnabled: Boolean,
    otherCaptureActive: Boolean
) {
    // Live elapsed timer — ticks every second while a capture is active
    var elapsedSeconds by remember(captureStartedAt) { mutableStateOf(0L) }
    if (captureState == CaptureState.CAPTURING && captureStartedAt > 0L) {
        LaunchedEffect(captureStartedAt) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - captureStartedAt) / 1000
                delay(1000L)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val chipLabel = when (captureState) {
            CaptureState.IDLE -> "IDLE"
            CaptureState.CAPTURING -> {
                val mins = elapsedSeconds / 60
                val secs = (elapsedSeconds % 60).toString().padStart(2, '0')
                "● CAPTURING — $mins:$secs${liveCount?.let { " — $it devices" } ?: ""}"
            }
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
    onViewDevice: (String) -> Unit,
    learnedSignature: KnownTargetSignature?,
    learnedMatchResults: Map<String, KnownTargetMatchResult>,
    onLearnDevice: (String) -> Unit,
    onClearLearnedDevice: () -> Unit
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

        // Learned device status banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (learnedSignature != null)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (learnedSignature != null)
                        "Learned device profile: ${learnedSignature.displayName}"
                    else
                        "No learned glasses profile saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (learnedSignature != null)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (learnedSignature != null) {
                    TextButton(onClick = onClearLearnedDevice) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Compare summary — quick stats for validation
        if (safeResults.isNotEmpty()) {
            CompareSummaryRow(results = safeResults)
        }

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
                        isFirstResult = index == 0,
                        onViewDevice = onViewDevice,
                        learnedSignature = learnedSignature,
                        learnedMatchResult = learnedMatchResults[result.capturedDevice.fingerprintId],
                        onLearnDevice = onLearnDevice
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareSummaryRow(results: List<CompareMatchResult>) {
    val targetOnlyCount = results.count { !it.seenInBaseline }
    val metaCount = results.count { it.capturedDevice.manufacturerIds.contains(0x0075) }
    val topScore = results.maxOfOrNull { it.score } ?: 0
    val topConfidence = results.firstOrNull()?.confidence ?: CompareConfidence.NONE
    val hasStrongMatch = results.any {
        it.confidence == CompareConfidence.HIGH || it.confidence == CompareConfidence.MEDIUM
    }

    val summaryColor = when {
        hasStrongMatch && metaCount > 0 -> MaterialTheme.colorScheme.primary
        hasStrongMatch -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Target-only: $targetOnlyCount  |  Meta ID: $metaCount  |  Top score: $topScore  |  Top: ${topConfidence.name}",
                style = MaterialTheme.typography.labelSmall,
                color = summaryColor
            )
            Text(
                if (hasStrongMatch && metaCount > 0)
                    "✓ Strong match with Meta manufacturer evidence"
                else if (hasStrongMatch)
                    "✓ Strong match found — no Meta manufacturer ID yet"
                else if (metaCount > 0)
                    "Meta manufacturer ID present — score below MEDIUM threshold"
                else
                    "No strong differential match — all results LOW or below",
                style = MaterialTheme.typography.labelSmall,
                color = summaryColor
            )
        }
    }
}

@Composable
private fun CompareResultCard(
    result: CompareMatchResult,
    isTopCandidate: Boolean,
    isFirstResult: Boolean = false,
    onViewDevice: (String) -> Unit,
    learnedSignature: KnownTargetSignature?,
    learnedMatchResult: KnownTargetMatchResult?,
    onLearnDevice: (String) -> Unit
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

            // Behavioral detection labels — shown when device has no identity signal
            val hasIdentitySignal = result.comparisonSignals.any { s ->
                s.startsWith("Meta manufacturer") ||
                s.startsWith("Classification: SMART_GLASSES") ||
                s.startsWith("Classification: CAMERA_CAPABLE_WEARABLE") ||
                s.startsWith("Exact target name") ||
                s.startsWith("Partial target profile")
            }
            val hasProximityPersistenceCombo = result.comparisonSignals.any {
                it.startsWith("Close proximity + persistence")
            }
            if (!hasIdentitySignal) {
                Text(
                    "No identity signal — using behavioral detection",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasProximityPersistenceCombo) {
                Text(
                    "Likely nearby device (strong signal + persistence)",
                    style = MaterialTheme.typography.labelSmall,
                    color = confidenceColor
                )
            }

            // Label override — learned match takes priority over raw BLE label
            val learnedLabel: String? = when (learnedMatchResult?.confidence) {
                KnownMatchConfidence.STRONG -> learnedMatchResult.signature.displayName
                KnownMatchConfidence.POSSIBLE -> "Possible match to your glasses"
                else -> null
            }
            val rawLabel = device.advertisedName
                ?: device.companyNames.firstOrNull()?.let { "$it device" }
                ?: "Unknown BLE Device"

            if (learnedLabel != null) {
                Text(
                    learnedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    rawLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(rawLabel, style = MaterialTheme.typography.bodyMedium)
            }

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

            // Learn button — shown on first result (index == 0), hidden if already saved
            val alreadySaved = learnedSignature?.fingerprintId == device.fingerprintId
            if (isFirstResult && !alreadySaved) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onLearnDevice(device.fingerprintId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Learn this device — This is my glasses")
                }
            }
        }
    }
}
