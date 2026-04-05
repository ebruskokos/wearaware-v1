package com.wearaware.app.domain.model

/**
 * GATT services and characteristics discovered from a single connection attempt.
 * [deviceAddress] is the BLE MAC used to initiate the connection — not persisted.
 * [characteristicUuids] maps each service UUID to the list of characteristic UUIDs it exposes.
 */
data class GattDiscoveryResult(
    val deviceAddress: String,
    val serviceUuids: List<String>,
    val characteristicUuids: Map<String, List<String>>,
    val discoveredAt: Long
)
