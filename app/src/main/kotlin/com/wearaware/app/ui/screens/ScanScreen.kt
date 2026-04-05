package com.wearaware.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.RankedCandidate
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
    onPairAndLearnClick: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Reload learned signature each time ScanScreen becomes active — picks up signatures
    // saved via CaptureScreen (which uses a different ViewModel instance).
    LaunchedEffect(Unit) {
        viewModel.reloadLearnedSignature()
    }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.BLE_PERMISSIONS.toList()
    ) { results ->
        if (PermissionUtils.allGranted(results)) viewModel.startScanning()
        else viewModel.setPermissionsRequired()
    }

    // Background/foreground resilience: stop scanning when app is paused, resume when foregrounded.
    // This prevents battery drain and complies with Android BLE background restrictions.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.stopScanning()
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.reloadLearnedSignature()
                    if (permissionsState.allPermissionsGranted) {
                        viewModel.startScanning()
                    } else {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WearAware") },
                actions = {
                    TextButton(onClick = onPairAndLearnClick) {
                        Text(
                            text = "Pair & Learn",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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
                        TextButton(onClick = { viewModel.toggleDebugOverlay() }) {
                            Text(
                                text = if (uiState.debugOverlayVisible) "Overlay Off" else "Overlay",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (uiState.knownTargetSignature != null) {
                        TextButton(onClick = { viewModel.toggleRelearnMode() }) {
                            Text(
                                text = if (uiState.relearnModeActive) "Relearn: ON" else "Relearn",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.relearnModeActive)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface
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

            // Learned device status chip
            val learnedSig = uiState.knownTargetSignature
            if (learnedSig != null) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            "${learnedSig.displayName} profile active",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
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

            // Ranked candidates (while scanning, only when signature is loaded)
            if (uiState.scanState == ScanState.SCANNING && uiState.rankedCandidates.isNotEmpty()) {
                RankedCandidatesSection(
                    candidates = uiState.rankedCandidates,
                    debugMode = uiState.debugMode,
                    onDeviceClick = onDeviceClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Debug overlay strip — lock status / top candidate score / RSSI
            if (uiState.debugOverlayVisible && uiState.scanState == ScanState.SCANNING) {
                DebugOverlayStrip(uiState = uiState)
            }

            // Session report (shown after scan stops)
            val report = uiState.lastSessionReport
            if (uiState.scanState == ScanState.STOPPED && report != null) {
                SessionReportCard(
                    report = report,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Reset Learning button (shown when stopped and signature exists)
            if (uiState.scanState == ScanState.STOPPED && uiState.knownTargetSignature != null) {
                var showConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset Learning", style = MaterialTheme.typography.labelSmall)
                }
                if (showConfirm) {
                    AlertDialog(
                        onDismissRequest = { showConfirm = false },
                        title = { Text("Reset Learning?") },
                        text = { Text("This will permanently delete the learned signature and all history.") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.resetLearning(); showConfirm = false }) {
                                Text("Reset", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            // Adaptive refinement indicator
            if (uiState.isRefiningSignature) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Refining signature...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (uiState.lastRefinementDelta != null && uiState.scanState == ScanState.SCANNING) {
                Text(
                    text = "Signature updated: ${uiState.lastRefinementDelta}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
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
                            isTopCandidate = matchResult?.isTopCandidate == true,
                            learnedMatchResult = uiState.learnedMatchResults[device.id]
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

@Composable
private fun RankedCandidatesSection(
    candidates: List<RankedCandidate>,
    debugMode: Boolean,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Target Candidates",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Stable key-based iteration avoids full recomposition on rank changes
            candidates.forEach { candidate ->
                key(candidate.device.id) {
                    RankedCandidateRow(
                        candidate = candidate,
                        debugMode = debugMode,
                        onClick = { onDeviceClick(candidate.device.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RankedCandidateRow(
    candidate: RankedCandidate,
    debugMode: Boolean,
    onClick: () -> Unit
) {
    val confidenceColor = when (candidate.matchResult.confidence) {
        KnownMatchConfidence.STRONG -> MaterialTheme.colorScheme.primary
        KnownMatchConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
        KnownMatchConfidence.WEAK -> MaterialTheme.colorScheme.tertiary
        KnownMatchConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rankLabel = when (candidate.rank) {
        1 -> "#1"
        2 -> "#2"
        3 -> "#3"
        else -> "#${candidate.rank}"
    }
    val lockBadge = if (candidate.isPrimaryLock) " 🔒" else ""
    val displayName = if (candidate.matchResult.labelOverrideActive)
        candidate.matchResult.signature.displayName
    else
        candidate.device.advertisedName ?: candidate.device.id.take(12)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.rank == 1 && candidate.isPrimaryLock)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$rankLabel$lockBadge",
                        style = MaterialTheme.typography.titleSmall,
                        color = confidenceColor
                    )
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    candidate.matchResult.confidence.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = confidenceColor
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    "Score ${candidate.matchResult.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Peak ${candidate.lifecycle.peakScore}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${candidate.lifecycle.totalSeenCount} ticks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (debugMode) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    candidate.rankReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                candidate.matchResult.temporalNotes.forEach { note ->
                    Text(
                        "  • $note",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugOverlayStrip(
    uiState: com.wearaware.app.ui.viewmodel.ScanUiState
) {
    val lockedId = uiState.primaryLockDeviceId
    val topResult = if (lockedId != null) uiState.learnedMatchResults[lockedId]
                    else uiState.rankedCandidates.firstOrNull()?.matchResult
    val topDevice = if (lockedId != null) uiState.devices.find { it.id == lockedId }
                    else uiState.rankedCandidates.firstOrNull()?.device

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (lockedId != null) {
                Text(
                    "LOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            } else {
                Text(
                    "NO LOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                )
            }
            if (topResult != null) {
                Text(
                    "Score: ${topResult.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Text(
                    topResult.confidence.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (topResult.confidence) {
                        KnownMatchConfidence.STRONG -> MaterialTheme.colorScheme.inversePrimary
                        KnownMatchConfidence.POSSIBLE -> MaterialTheme.colorScheme.inverseOnSurface
                        else -> MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                    }
                )
            }
            if (topDevice != null) {
                Text(
                    "${topDevice.averagedRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

@Composable
private fun SessionReportCard(
    report: com.wearaware.app.domain.model.SessionReport,
    modifier: Modifier = Modifier
) {
    val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    val durationSec = report.sessionDurationMs / 1000
    val lockedSec = report.totalLockedMs / 1000

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last Session Report", style = MaterialTheme.typography.titleSmall)
            Text(
                "Duration: ${durationSec}s  •  Locked: ${lockedSec}s",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Candidates seen: ${report.totalCandidatesSeen}  •  Lock switches: ${report.lockSwitchCount}",
                style = MaterialTheme.typography.bodySmall
            )
            if (report.maxCompetingScore > 0) {
                Text(
                    "Max competing score: ${report.maxCompetingScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (report.maxCompetingScore >= 8) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            report.lockedDeviceId?.let {
                Text(
                    "Locked device: ${it.take(20)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Ended: ${fmt.format(report.endedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
