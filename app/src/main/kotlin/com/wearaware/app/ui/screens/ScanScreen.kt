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
        modifier = modifier.fillMaxWidth(),
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
            candidates.forEach { candidate ->
                RankedCandidateRow(
                    candidate = candidate,
                    debugMode = debugMode,
                    onClick = { onDeviceClick(candidate.device.id) }
                )
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
