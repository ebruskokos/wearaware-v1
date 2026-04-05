package com.wearaware.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.wearaware.app.domain.model.LearningSessionStatus
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.ui.viewmodel.LearningLogViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningLogScreen(
    onBack: () -> Unit,
    onSessionClick: (String) -> Unit,
    viewModel: LearningLogViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No learning sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    SessionCard(session = session, onClick = { onSessionClick(session.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: PairedLearningSession, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val statusColor = when (session.status) {
        LearningSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        LearningSessionStatus.FAILED -> MaterialTheme.colorScheme.error
        LearningSessionStatus.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
        LearningSessionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(fmt.format(Date(session.startedAt)), style = MaterialTheme.typography.bodyMedium)
                Text(session.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${session.eventCount} events", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.fingerprintId?.let {
                Text("Fingerprint: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
