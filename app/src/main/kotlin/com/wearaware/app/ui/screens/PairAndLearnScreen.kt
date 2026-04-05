package com.wearaware.app.ui.screens

import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.ui.viewmodel.PairAndLearnEffect
import com.wearaware.app.ui.viewmodel.PairAndLearnViewModel
import com.wearaware.app.ui.viewmodel.PairingFlowState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairAndLearnScreen(
    onBack: () -> Unit,
    onViewLog: () -> Unit,
    onViewSignature: () -> Unit = {},
    viewModel: PairAndLearnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cdmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onCdmResult(result)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PairAndLearnEffect.LaunchCdmPicker -> {
                    cdmLauncher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair & Learn My Glasses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onViewSignature) {
                        Text("Signature", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onViewLog) {
                        Text("History", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val existing = uiState.existingSignature
            if (existing != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Learned device profile active", style = MaterialTheme.typography.titleSmall)
                        Text(existing.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Saved ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(existing.savedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (existing.gattServiceUuids.isNotEmpty()) {
                            Text(
                                "GATT services: ${existing.gattServiceUuids.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (existing.characteristicValuePrefixes.isNotEmpty()) {
                            Text(
                                "Characteristic reads: ${existing.characteristicValuePrefixes.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Learned ${existing.learnCount} time(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Training toggle
                            if (uiState.isTraining) {
                                OutlinedButton(onClick = { viewModel.stopTraining() }) {
                                    Text("Stop Training")
                                }
                            } else {
                                OutlinedButton(onClick = { viewModel.startTraining() }) {
                                    Text("Start Training")
                                }
                            }
                            // Export
                            val coroutineScope = rememberCoroutineScope()
                            OutlinedButton(onClick = {
                                coroutineScope.launch {
                                    val json = viewModel.buildExportJson()
                                    if (json != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_TEXT, json)
                                            putExtra(Intent.EXTRA_SUBJECT, "WearAware learned profile export")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export logs"))
                                    }
                                }
                            }) {
                                Text("Export Logs")
                            }
                        }
                        TextButton(
                            onClick = { viewModel.clearProfile() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Remove profile") }
                    }
                }
                if (uiState.isTraining) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Training active — logging BLE signals every 5s",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (uiState.flowState) {
                PairingFlowState.IDLE -> {
                    Text(
                        "Pair your glasses so WearAware can recognize them on future scans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cdm = context.getSystemService(CompanionDeviceManager::class.java)
                            viewModel.startPairing(cdm)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (existing != null) "Pair new device (replaces current)" else "Pair & Learn My Glasses")
                    }
                }

                PairingFlowState.CDM_SCANNING,
                PairingFlowState.CDM_WAITING_SELECTION,
                PairingFlowState.CDM_ASSOCIATED,
                PairingFlowState.GATT_CONNECTING,
                PairingFlowState.GATT_DISCOVERING,
                PairingFlowState.LEARNING -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.statusMessage, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stepHintFor(uiState.flowState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PairingFlowState.COMPLETED -> {
                    Text("✓", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Profile saved!", style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.existingSignature?.displayName ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                    TextButton(onClick = { viewModel.resetToIdle() }) {
                        Text("Pair a different device")
                    }
                }

                PairingFlowState.FAILED -> {
                    Text(
                        uiState.errorMessage ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.resetToIdle() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

private fun stepHintFor(state: PairingFlowState): String = when (state) {
    PairingFlowState.CDM_SCANNING -> "Searching for nearby BLE devices..."
    PairingFlowState.CDM_WAITING_SELECTION -> "Select your glasses from the system dialog"
    PairingFlowState.CDM_ASSOCIATED -> "Device selected"
    PairingFlowState.GATT_CONNECTING -> "Opening Bluetooth connection..."
    PairingFlowState.GATT_DISCOVERING -> "Reading device capabilities..."
    PairingFlowState.LEARNING -> "Building your glasses profile..."
    else -> ""
}
