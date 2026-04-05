package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.ui.viewmodel.LearningSessionDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: LearningSessionDetailViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val session by viewModel.session.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            session?.let {
                item {
                    Text("Status: ${it.status.name}", style = MaterialTheme.typography.titleSmall)
                    it.fingerprintId?.let { fp ->
                        Text("Fingerprint: $fp", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(events) { event ->
                EventRow(event = event)
            }
        }
    }
}

@Composable
private fun EventRow(event: PairedLearningEvent) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = when (event.eventType) {
        LearningEventType.GATT_FAILED, LearningEventType.CDM_FAILED ->
            MaterialTheme.colorScheme.error
        LearningEventType.SIGNATURE_BUILT, LearningEventType.SESSION_COMPLETED ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fmt.format(Date(event.occurredAt)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(event.eventType.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = color)
            event.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
