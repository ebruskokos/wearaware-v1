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
import com.wearaware.app.domain.model.ScoreCategory
import com.wearaware.app.domain.model.SignatureConfidence
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
                val isNonTarget = deviceId in uiState.persistentNonTargetIds
                val confidenceLabel = when {
                    isNonTarget -> "Persistent non-target (high score, never locked)"
                    learnedMatchResult.confidence == KnownMatchConfidence.STRONG ->
                        "Strong match — \"${learnedMatchResult.signature.displayName}\""
                    learnedMatchResult.confidence == KnownMatchConfidence.POSSIBLE ->
                        "Possible match to your glasses"
                    learnedMatchResult.confidence == KnownMatchConfidence.WEAK ->
                        "Weak match (limited signals)"
                    else -> "No match to learned profile"
                }
                Text(
                    text = confidenceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNonTarget) MaterialTheme.colorScheme.error else confidenceColor
                )
                Text(
                    text = "Score: ${learnedMatchResult.score}  •  Label override: ${if (learnedMatchResult.labelOverrideActive) "active" else "off"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Structured score breakdown
                if (learnedMatchResult.scoreBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Score breakdown:", style = MaterialTheme.typography.labelSmall)
                    learnedMatchResult.scoreBreakdown
                        .sortedByDescending { kotlin.math.abs(it.points) }
                        .forEach { item ->
                            val sign = if (item.points >= 0) "+" else ""
                            val color = when {
                                item.points >= 4 -> MaterialTheme.colorScheme.primary
                                item.points > 0 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                            val categoryLabel = when (item.category) {
                                ScoreCategory.MANUFACTURER_ID -> "Manufacturer ID"
                                ScoreCategory.PREFIX_MATCH -> "Data prefix"
                                ScoreCategory.SERVICE_UUID -> "Service UUID"
                                ScoreCategory.GATT_UUID -> "GATT UUID"
                                ScoreCategory.FINGERPRINT_ID -> "Fingerprint ID"
                                ScoreCategory.PROXIMITY -> "Proximity"
                                ScoreCategory.PERSISTENCE -> "Persistence"
                                ScoreCategory.CONNECTABLE -> "Connectable"
                                ScoreCategory.VISIBLE_AT_STOP -> "Visible at stop"
                                ScoreCategory.APPLE_PENALTY -> "Apple penalty"
                                ScoreCategory.WEAK_SIGNAL -> "Weak signal"
                                ScoreCategory.LOW_PERSISTENCE -> "Low persistence"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "  $categoryLabel: ${item.description}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$sign${item.points}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                        }
                }
                if (learnedMatchResult.temporalNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Temporal filter:", style = MaterialTheme.typography.labelSmall)
                    learnedMatchResult.temporalNotes.forEach { note ->
                        Text(
                            text = "  • $note",
                            style = MaterialTheme.typography.labelSmall,
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
                    Spacer(modifier = Modifier.height(4.dp))

                    // RSSI trend: compare live device RSSI to learned average
                    val liveRssi = device.averagedRssi
                    val learnedAvg = profile.typicalRssiAtClose
                    val rssiDelta = liveRssi - learnedAvg
                    val trendLabel = when {
                        rssiDelta >= 5 -> "↑ Stronger than usual (+${rssiDelta} dBm)"
                        rssiDelta <= -5 -> "↓ Weaker than usual (${rssiDelta} dBm)"
                        else -> "≈ Typical signal (${if (rssiDelta >= 0) "+$rssiDelta" else "$rssiDelta"} dBm)"
                    }
                    val trendColor = when {
                        rssiDelta >= 5 -> MaterialTheme.colorScheme.primary
                        rssiDelta <= -5 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Signal now: ${liveRssi} dBm",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(trendLabel, style = MaterialTheme.typography.bodySmall, color = trendColor)
                    }
                    Text(
                        "Learned avg: ${learnedAvg} dBm  (${profile.rssiSampleCount.coerceAtLeast(1)} samples)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // RSSI quality bar: map -40 (best) to -90 (worst) → 0..1
                    val rssiProgress = ((liveRssi.coerceIn(-90, -40) + 90).toFloat() / 50f)
                    val rssiBarColor = when {
                        liveRssi >= -60 -> MaterialTheme.colorScheme.primary
                        liveRssi >= -70 -> MaterialTheme.colorScheme.secondary
                        liveRssi >= -80 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    LinearProgressIndicator(
                        progress = { rssiProgress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        color = rssiBarColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (profile.maxSeenCount > 0) {
                        // Persistence range bar
                        val persistencePct = (device.seenCount.coerceIn(0, profile.maxSeenCount)
                            .toFloat() / profile.maxSeenCount.coerceAtLeast(1).toFloat())
                        Text(
                            "Persistence: ${device.seenCount} now  •  range ${profile.minSeenCount}–${profile.maxSeenCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { persistencePct },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (profile.totalObservations > 0) {
                        val visiblePct = (profile.visibleAtStopCount * 100) / profile.totalObservations
                        Text(
                            "Visible at stop: ${profile.visibleAtStopCount}/${profile.totalObservations} sessions ($visiblePct%)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (sig.manufacturerDataPrefixes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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

            // --- Training Health ---
            val healthSig = uiState.knownTargetSignature
            if (healthSig != null) {
                HorizontalDivider()
                TrainingHealthSection(sig = healthSig)
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

@Composable
private fun TrainingHealthSection(
    sig: com.wearaware.app.domain.model.KnownTargetSignature
) {
    val profile = sig.behaviorProfile
    val fmt = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Training Health", style = MaterialTheme.typography.titleSmall)

        // Confidence badge
        val (confLabel, confColor) = when (sig.signatureConfidence) {
            SignatureConfidence.HIGH -> "HIGH confidence" to MaterialTheme.colorScheme.primary
            SignatureConfidence.MEDIUM -> "MEDIUM confidence" to MaterialTheme.colorScheme.secondary
            SignatureConfidence.LOW -> "LOW confidence — needs more training" to MaterialTheme.colorScheme.error
        }
        Text(
            text = confLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = confColor
        )

        // Observation count + last updated
        Text(
            text = "Observations: ${sig.observationCount}  •  Version: v${sig.version}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Last updated: ${fmt.format(sig.lastUpdatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (profile != null) {
            // RSSI range (variance proxy): smaller range = more stable
            val rssiMin = profile.rssiMin
            val rssiMax = profile.rssiMax
            if (rssiMin != null && rssiMax != null && rssiMax > rssiMin) {
                val range = rssiMax - rssiMin
                val stabilityLabel = when {
                    range <= 10 -> "Stable (±${range / 2} dBm)"
                    range <= 20 -> "Moderate variance (${range} dBm range)"
                    else -> "High variance (${range} dBm range)"
                }
                val stabilityColor = when {
                    range <= 10 -> MaterialTheme.colorScheme.primary
                    range <= 20 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Text(
                    text = "Signal variance: $stabilityLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = stabilityColor
                )
                // Stability bar: 0 = full range, 1 = no variance
                val stabilityProgress = (1f - (range.coerceIn(0, 40).toFloat() / 40f))
                LinearProgressIndicator(
                    progress = { stabilityProgress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = stabilityColor
                )
            }

            // Sample depth
            val sampleDepth = profile.rssiSampleCount.coerceAtLeast(1)
            Text(
                text = "RSSI samples: $sampleDepth  •  Avg: ${profile.typicalRssiAtClose} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Stability score: observations / 30, capped at 100%
            val stabilityScore = (sig.observationCount.coerceAtMost(30) * 100) / 30
            Text(
                text = "Stability score: $stabilityScore%",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { stabilityScore / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                color = when {
                    stabilityScore >= 80 -> MaterialTheme.colorScheme.primary
                    stabilityScore >= 40 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )

            // Cross-session RSSI trend
            if (profile.sessionRssiAverages.size >= 2) {
                val oldest = profile.sessionRssiAverages.first()
                val newest = profile.sessionRssiAverages.last()
                val trend = newest - oldest
                val trendLabel = when {
                    trend >= 5 -> "↑ Getting closer over sessions (+${trend} dBm avg)"
                    trend <= -5 -> "↓ Getting farther over sessions (${trend} dBm avg)"
                    else -> "≈ Stable across ${profile.sessionRssiAverages.size} sessions"
                }
                Text(
                    text = trendLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
