package com.wearaware.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.KnownBehaviorProfile
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.ui.viewmodel.SignatureLogViewerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureLogViewerScreen(
    onBack: () -> Unit,
    viewModel: SignatureLogViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signature & Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.exportJson?.let { json ->
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, json)
                                putExtra(Intent.EXTRA_SUBJECT, "WearAware Signature Export")
                            }
                            context.startActivity(Intent.createChooser(intent, "Export"))
                        }) { Text("Share") }
                    }
                    TextButton(onClick = { viewModel.toggleRawJson() }) {
                        Text(if (uiState.showRawJson) "Parsed" else "Raw JSON")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.signature == null) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No learned profile yet. Use Pair & Learn to train WearAware on your glasses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }

        if (uiState.showRawJson) {
            RawJsonView(json = uiState.exportJson ?: "", innerPadding = innerPadding)
        } else {
            ParsedView(
                sig = uiState.signature!!,
                sessionCount = uiState.sessions.size,
                latestEvents = uiState.latestSessionEvents,
                innerPadding = innerPadding
            )
        }
    }
}

@Composable
private fun ParsedView(
    sig: KnownTargetSignature,
    sessionCount: Int,
    latestEvents: List<PairedLearningEvent>,
    innerPadding: PaddingValues
) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Identity ---
        item {
            SectionCard(title = "Device Identity") {
                InfoRow("Name", sig.displayName)
                InfoRow("Fingerprint ID", sig.fingerprintId)
                InfoRow("Saved", fmt.format(Date(sig.savedAt)))
                InfoRow("Last updated", fmt.format(Date(sig.lastUpdatedAt)))
                InfoRow("Learn count", sig.learnCount.toString())
            }
        }

        // --- Fingerprint fields ---
        item {
            SectionCard(title = "Fingerprint") {
                if (sig.manufacturerIds.isEmpty()) {
                    InfoRow("Manufacturer IDs", "none")
                } else {
                    InfoRow(
                        "Manufacturer IDs",
                        sig.manufacturerIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
                    )
                }
                InfoRow("Manufacturer data prefixes", if (sig.manufacturerDataPrefixes.isEmpty()) "none" else "")
                sig.manufacturerDataPrefixes.forEach { prefix ->
                    val freq = sig.manufacturerDataPrefixFrequency[prefix]
                    val freqLabel = if (freq != null) " (×$freq sessions)" else ""
                    BulletRow("$prefix$freqLabel")
                }
                InfoRow("Service UUIDs", if (sig.serviceUuids.isEmpty()) "none" else "")
                sig.serviceUuids.forEach { BulletRow(it) }
                if (sig.gattServiceUuids.isNotEmpty()) {
                    InfoRow("GATT service UUIDs", "")
                    sig.gattServiceUuids.forEach { BulletRow(it) }
                }
                if (sig.characteristicValuePrefixes.isNotEmpty()) {
                    InfoRow("Characteristic reads", "")
                    sig.characteristicValuePrefixes.forEach { (uuid, hex) ->
                        BulletRow("$uuid → $hex")
                    }
                }
            }
        }

        // --- Behavior profile ---
        sig.behaviorProfile?.let { profile ->
            item { BehaviorProfileCard(profile = profile) }
        }

        // --- Session summary ---
        item {
            SectionCard(title = "Learning Sessions") {
                InfoRow("Total sessions", sessionCount.toString())
                if (latestEvents.isEmpty()) {
                    Text(
                        "No events from latest session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Latest session events ---
        if (latestEvents.isNotEmpty()) {
            item {
                Text(
                    "Latest session (${latestEvents.size} events)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(latestEvents) { event ->
                CompactEventRow(event = event)
            }
        }
    }
}

@Composable
private fun BehaviorProfileCard(profile: KnownBehaviorProfile) {
    SectionCard(title = "Behavior Profile") {
        InfoRow("Avg RSSI", "${profile.typicalRssiAtClose} dBm (${profile.rssiSampleCount} samples)")
        InfoRow("Seen count range", "${profile.minSeenCount} – ${profile.maxSeenCount}")

        // RSSI quality indicator
        val rssiQuality = when {
            profile.typicalRssiAtClose >= -60 -> "Excellent"
            profile.typicalRssiAtClose >= -70 -> "Good"
            profile.typicalRssiAtClose >= -80 -> "Fair"
            else -> "Weak"
        }
        val rssiColor = when (rssiQuality) {
            "Excellent" -> MaterialTheme.colorScheme.primary
            "Good" -> MaterialTheme.colorScheme.secondary
            "Fair" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Signal quality:", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rssiQuality, style = MaterialTheme.typography.bodySmall, color = rssiColor)
        }

        if (profile.totalObservations > 0) {
            val pct = (profile.visibleAtStopCount * 100) / profile.totalObservations
            InfoRow("Visible at stop", "${profile.visibleAtStopCount}/${profile.totalObservations} sessions ($pct%)")

            // Persistence bar
            Spacer(modifier = Modifier.height(4.dp))
            Text("Persistence (visible at stop):", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = when {
                    pct >= 70 -> MaterialTheme.colorScheme.primary
                    pct >= 40 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )
        }
        InfoRow("Total observations", profile.totalObservations.toString())
    }
}

@Composable
private fun RawJsonView(json: String, innerPadding: PaddingValues) {
    val scrollH = rememberScrollState()
    val scrollV = rememberScrollState()
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .horizontalScroll(scrollH)
                .verticalScroll(scrollV)
        ) {
            Text(
                text = json,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BulletRow(text: String) {
    Text(
        "  • $text",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CompactEventRow(event: PairedLearningEvent) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val isError = event.eventType == LearningEventType.GATT_FAILED ||
        event.eventType == LearningEventType.CDM_FAILED
    val isSuccess = event.eventType == LearningEventType.SIGNATURE_BUILT ||
        event.eventType == LearningEventType.SESSION_COMPLETED
    val labelColor = when {
        isError -> MaterialTheme.colorScheme.error
        isSuccess -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(fmt.format(Date(event.occurredAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 64.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.eventType.name.replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor)
                event.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
