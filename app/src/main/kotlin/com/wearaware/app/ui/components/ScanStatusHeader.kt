package com.wearaware.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wearaware.app.ui.viewmodel.ScanState

@Composable
fun ScanStatusHeader(
    scanState: ScanState,
    deviceCount: Int,
    modifier: Modifier = Modifier
) {
    val statusText = when (scanState) {
        ScanState.SCANNING               -> "Scanning nearby devices…"
        ScanState.STOPPED                -> "Scanning stopped"
        ScanState.BLUETOOTH_UNAVAILABLE  -> "Bluetooth unavailable"
        ScanState.PERMISSIONS_REQUIRED   -> "Permissions required"
    }
    val subtitleText = when {
        scanState == ScanState.SCANNING && deviceCount == 0 -> "No nearby devices detected"
        scanState == ScanState.SCANNING                     -> "$deviceCount device${if (deviceCount == 1) "" else "s"} detected"
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        subtitleText?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
