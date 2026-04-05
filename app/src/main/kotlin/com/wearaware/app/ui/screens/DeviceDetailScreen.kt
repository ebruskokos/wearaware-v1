package com.wearaware.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.BleDebugData
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.ui.components.SafeWording
import com.wearaware.app.ui.components.SignalBars
import com.wearaware.app.ui.components.VisibilityBadge
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.formatDuration
import com.wearaware.app.util.formatFullTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    onPairAndLearnClick: () -> Unit = {},
    onViewSignature: () -> Unit = {},
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val device: ObservedDevice? = uiState.devices.firstOrNull { it.id == deviceId }
    val matchResult = uiState.deviceMatchScores[deviceId]
    val learnedMatchResult = uiState.learnedMatchResults[deviceId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

            // --- Known Device Match ---
            HorizontalDivider()
            Text("Known Device Match", style = MaterialTheme.typography.titleSmall)
            if (uiState.knownTargetSignature == null) {
                Text(
                    "No learned profile — use \"Pair & Learn\" to train WearAware on your glasses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (learnedMatchResult == null) {
                Text(
                    "Profile loaded but no match data yet — start a scan to compute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val confidenceColor = when (learnedMatchResult.confidence) {
                    KnownMatchConfidence.STRONG -> MaterialTheme.colorScheme.primary
                    KnownMatchConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
                    KnownMatchConfidence.WEAK -> MaterialTheme.colorScheme.tertiary
                    KnownMatchConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val confidenceLabel = when (learnedMatchResult.confidence) {
                    KnownMatchConfidence.STRONG -> "Strong match — \"${learnedMatchResult.signature.displayName}\""
                    KnownMatchConfidence.POSSIBLE -> "Possible match to your glasses"
                    KnownMatchConfidence.WEAK -> "Weak match (limited signals)"
                    KnownMatchConfidence.NONE -> "No match to learned profile"
                }
                Text(
                    text = confidenceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = confidenceColor
                )
                Text(
                    text = "Score: ${learnedMatchResult.score}  •  Label override: ${if (learnedMatchResult.labelOverrideActive) "active" else "off"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (learnedMatchResult.matchedSignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Why it matched:", style = MaterialTheme.typography.labelSmall)
                    learnedMatchResult.matchedSignals.forEach { signal ->
                        Text(
                            text = "  • $signal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Training & Learned Profile ---
            val sig = uiState.knownTargetSignature
            if (sig != null && learnedMatchResult != null &&
                (learnedMatchResult.confidence == KnownMatchConfidence.STRONG ||
                    learnedMatchResult.confidence == KnownMatchConfidence.POSSIBLE)) {
                HorizontalDivider()
                Text("Training & Learned Profile", style = MaterialTheme.typography.titleSmall)

                val profile = sig.behaviorProfile
                val fmt = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())

                Text(
                    "Observations: ${sig.learnCount}  •  Last updated: ${fmt.format(sig.lastUpdatedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (profile != null) {
                    Text(
                        "Avg RSSI: ${profile.typicalRssiAtClose} dBm  " +
                            "(${profile.rssiSampleCount.coerceAtLeast(1)} samples)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (profile.maxSeenCount > 0) {
                        Text(
                            "Persistence range: ${profile.minSeenCount}–${profile.maxSeenCount} observations",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (profile.totalObservations > 0) {
                        val visiblePct = if (profile.totalObservations > 0)
                            (profile.visibleAtStopCount * 100) / profile.totalObservations else 0
                        Text(
                            "Visible at stop: ${profile.visibleAtStopCount}/${profile.totalObservations} sessions ($visiblePct%)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (sig.manufacturerDataPrefixes.isNotEmpty()) {
                    Text("Learned prefixes (top ${sig.manufacturerDataPrefixes.size}):",
                        style = MaterialTheme.typography.labelSmall)
                    sig.manufacturerDataPrefixes.forEach { prefix ->
                        val freq = sig.manufacturerDataPrefixFrequency[prefix]
                        val freqLabel = if (freq != null) "  ×$freq" else ""
                        Text(
                            "  • $prefix$freqLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.refineFromObservation(deviceId) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Relearn", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onPairAndLearnClick,
                        modifier = Modifier.weight(1f)
                    ) { Text("Training", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onViewSignature,
                        modifier = Modifier.weight(1f)
                    ) { Text("Logs", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = withContext(Dispatchers.IO) { viewModel.buildExportJson() }
                                if (json != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        putExtra(Intent.EXTRA_SUBJECT, "WearAware Signature Export")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export Signature"))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export", style = MaterialTheme.typography.labelSmall) }
                }
            }

            // --- Debug View (shown when debug mode is on) ---
            if (uiState.debugMode) {
                HorizontalDivider()
                Text(
                    "Raw BLE Debug",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                // Identity
                Text("Fingerprint ID: ${device.id}", style = MaterialTheme.typography.labelSmall)
                Text("MAC address: ${device.macAddress ?: "n/a"}", style = MaterialTheme.typography.labelSmall)
                Text("BT device name: ${device.fingerprint?.normalizedName ?: "n/a"}", style = MaterialTheme.typography.labelSmall)

                // Manufacturer
                val fp = device.fingerprint
                if (fp != null) {
                    Text(
                        "Manufacturer IDs: ${fp.manufacturerIds.map { "0x${it.toString(16).uppercase().padStart(4, '0')}" }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    fp.manufacturerDataHex.forEach { (id, hex) ->
                        Text(
                            "  0x${id.toString(16).uppercase().padStart(4, '0')}: $hex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Service UUIDs
                if (fp?.serviceUuids?.isNotEmpty() == true) {
                    Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                    fp.serviceUuids.forEach { uuid ->
                        Text(
                            "  $uuid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // TX Power
                fp?.txPower?.let { Text("TX Power: $it dBm", style = MaterialTheme.typography.labelSmall) }

                // BleDebugData fields
                val dbg = device.rawBleData
                if (dbg != null) {
                    Text(
                        "Connectable: ${dbg.isConnectable}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    dbg.advertisingFlags?.let {
                        Text(
                            "Advertising flags: 0x${it.toString(16).uppercase()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Solicitation UUIDs
                    if (dbg.serviceSolicitationUuids.isNotEmpty()) {
                        Text("Solicitation UUIDs:", style = MaterialTheme.typography.labelSmall)
                        dbg.serviceSolicitationUuids.forEach { uuid ->
                            Text(
                                "  $uuid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Service data
                    if (dbg.serviceDataHex.isNotEmpty()) {
                        Text("Service data:", style = MaterialTheme.typography.labelSmall)
                        dbg.serviceDataHex.forEach { (uuid, hex) ->
                            Text(
                                "  $uuid: $hex",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // PHY + timing
                    if (dbg.primaryPhy != 0) {
                        Text(
                            "PHY: primary=${dbg.primaryPhy} secondary=${dbg.secondaryPhy}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (dbg.advertisingSid != 255 && dbg.advertisingSid != 0) {
                        Text("Advertising SID: ${dbg.advertisingSid}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (dbg.periodicAdvertisingInterval != 0) {
                        Text(
                            "Periodic interval: ${dbg.periodicAdvertisingInterval} × 1.25ms",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        "Device type: ${dbg.deviceType}  Bond state: ${dbg.bondState}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (dbg.timestampNanos != 0L) {
                        Text(
                            "Timestamp (nanos since boot): ${dbg.timestampNanos}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Raw scan bytes
                    dbg.rawScanBytesHex?.let { hex ->
                        Text("Raw scan bytes:", style = MaterialTheme.typography.labelSmall)
                        // Show max 64 chars then "..." to avoid overwhelming the screen
                        val preview = if (hex.length > 64) hex.take(64) + "…" else hex
                        Text(
                            preview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Raw BLE debug data unavailable for this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Classification + match
                Text(
                    "Classification rule: ${device.classification.matchedRuleId ?: "none"}",
                    style = MaterialTheme.typography.labelSmall
                )
                matchResult?.let {
                    Text(
                        "Target match score: ${it.score} (${it.confidence.name})",
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
