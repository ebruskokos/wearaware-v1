package com.wearaware.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.TargetMatchResult
import com.wearaware.app.util.formatDuration

@Composable
fun TargetMatchBanner(
    bestMatch: Pair<ObservedDevice, TargetMatchResult>?,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Best match for: Wayfarer 00ZS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (bestMatch == null || bestMatch.second.confidence == MatchConfidence.NONE) {
                Text(
                    text = "No strong match for your target device yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                val (device, match) = bestMatch
                val displayName = device.advertisedName
                    ?: device.companyNames.firstOrNull()?.let { "$it device" }
                    ?: device.classification.displayLabel.ifBlank { null }
                    ?: "BLE Device"

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (device.companyNames.isNotEmpty()) {
                    Text(
                        text = "Manufacturer: ${device.companyNames.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "MAC: ${device.macAddress ?: device.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = device.classification.displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Confidence: ${match.confidence.name} • Score: ${match.score} • ${device.averagedRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (match.matchedSignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Why this device:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    match.matchedSignals.forEach { signal ->
                        Text(
                            text = "  • $signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDeviceClick(device.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Device Details")
                }
            }
        }
    }
}
