package com.wearaware.app.domain.model

data class GattDiscoveryResult(
    val deviceAddress: String,
    val serviceUuids: List<String>,
    val characteristicUuids: Map<String, List<String>>,
    val discoveredAt: Long
)
