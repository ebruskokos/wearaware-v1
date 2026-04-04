package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.ui.viewmodel.SessionLogViewModel
import com.wearaware.app.util.formatFullTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogScreen(
    onBack: () -> Unit,
    viewModel: SessionLogViewModel = hiltViewModel()
) {
    val log by viewModel.log.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear session log")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Session Log") },
                text = { Text("All session log entries will be deleted. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearLog()
                        showClearDialog = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (log.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No log entries in this session.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(log.sortedByDescending { it.timestamp }) { entry ->
                    LogEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: ScanLogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.timestamp.formatFullTimestamp(),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = entry.advertisedName ?: "Unknown Device",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${entry.category} • ${entry.confidence} • ${entry.proximityLabel}",
                style = MaterialTheme.typography.bodySmall
            )
            entry.matchedRuleId?.let {
                Text(
                    text = "Rule: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
