package com.wearaware.app.domain.model

/**
 * GATT services and characteristics discovered from a single connection attempt.
 * [deviceAddress] is the BLE MAC used to initiate the connection — not persisted.
 * [characteristicUuids] maps each service UUID to the list of characteristic UUIDs it exposes.
 * [characteristicValues] maps each readable characteristic UUID to a hex prefix of its value
 *   (first 8 hex chars = 4 bytes). Empty if reads failed or characteristic was not readable.
 */
data class GattDiscoveryResult(
    val deviceAddress: String,
    val serviceUuids: List<String>,
    val characteristicUuids: Map<String, List<String>>,
    val discoveredAt: Long,
    val characteristicValues: Map<String, String> = emptyMap()
)
