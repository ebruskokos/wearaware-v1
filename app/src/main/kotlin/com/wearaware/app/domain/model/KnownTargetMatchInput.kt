package com.wearaware.app.domain.model

data class KnownTargetMatchInput(
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    val manufacturerDataPrefixes: List<String>,
    val serviceUuids: List<String>,
    val gattServiceUuids: List<String>,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean,
    val connectable: Boolean
)
