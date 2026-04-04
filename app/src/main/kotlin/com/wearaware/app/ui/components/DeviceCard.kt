package com.wearaware.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.util.formatDuration

@Composable
fun DeviceCard(
    device: ObservedDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal bars + visibility badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SignalBars(proximity = device.proximityLabel)
                Spacer(modifier = Modifier.height(4.dp))
                VisibilityBadge(state = device.visibilityState)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.advertisedName
                        ?: device.classification.matchedRuleId?.let { device.classification.displayLabel }
                        ?: "BLE Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = device.classification.displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = device.proximityLabel.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Seen: ${device.seenDurationMs.formatDuration()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View device details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
