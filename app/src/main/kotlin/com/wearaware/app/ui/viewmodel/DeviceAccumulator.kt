package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.TargetMatchResult

/**
 * Mutable accumulator for a single device observed during a capture window.
 * Identity fields are seeded from the first observation and enriched by later observations
 * when richer data becomes available (e.g. manufacturerIds appearing in a later scan tick).
 */
internal data class DeviceAccumulator(
    val fingerprintId: String,
    var advertisedName: String?,
    val macAddress: String?,
    var manufacturerIds: List<Int>,
    var manufacturerDataSummary: String?,
    var serviceUuids: List<String>,
    var category: DeviceCategory,
    var companyNames: List<String>,
    val firstSeenInCapture: Long,
    var lastSeenInCapture: Long,
    var peakRssi: Int,
    var runningRssiSum: Long,
    var readingCount: Int,
    var seenCount: Int,
    var targetMatchScore: Int?,
    var targetMatchSignals: List<String>
) {
    val averageRssi: Int get() = if (readingCount > 0) (runningRssiSum / readingCount).toInt() else peakRssi

    /**
     * Updates this accumulator with a new observation of the same device.
     *
     * RSSI stats are always updated. Identity fields (manufacturerIds, serviceUuids, etc.)
     * are upgraded from null/empty to the first richer value seen in any later tick —
     * this ensures a device first observed with incomplete advertising data eventually
     * retains its richest available identity before being saved as a CapturedDevice.
     * targetMatchScore/Signals are updated only when the new score is strictly higher.
     */
    fun updateFrom(device: ObservedDevice, matchResult: TargetMatchResult?, now: Long) {
        lastSeenInCapture = now
        seenCount++
        if (device.rawRssi > peakRssi) peakRssi = device.rawRssi
        runningRssiSum += device.rawRssi
        readingCount++

        // Replace null name with first observed non-null name
        if (advertisedName == null && device.advertisedName != null) {
            advertisedName = device.advertisedName
        }

        // Replace empty manufacturerIds with first observed non-empty set
        val newIds = device.fingerprint?.manufacturerIds ?: emptyList()
        if (manufacturerIds.isEmpty() && newIds.isNotEmpty()) {
            manufacturerIds = newIds
        }

        // Replace empty serviceUuids with first observed non-empty set
        val newUuids = device.fingerprint?.serviceUuids ?: emptyList()
        if (serviceUuids.isEmpty() && newUuids.isNotEmpty()) {
            serviceUuids = newUuids
        }

        // Upgrade category from UNKNOWN_BLE_DEVICE to any more specific classification
        if (category == DeviceCategory.UNKNOWN_BLE_DEVICE) {
            val newCategory = device.classification.category
            if (newCategory != DeviceCategory.UNKNOWN_BLE_DEVICE) {
                category = newCategory
            }
        }

        // Replace empty companyNames with first observed non-empty set
        if (companyNames.isEmpty() && device.companyNames.isNotEmpty()) {
            companyNames = device.companyNames
        }

        // Replace null manufacturer data summary with first observed non-null value
        val newMfDataSummary = device.fingerprint?.manufacturerDataHex
            ?.entries?.joinToString(",") { (id, hex) -> "${id.toString(16).padStart(4, '0')}:$hex" }
            ?.takeIf { it.isNotEmpty() }
        if (manufacturerDataSummary == null && newMfDataSummary != null) {
            manufacturerDataSummary = newMfDataSummary
        }

        // Always keep the highest target match score seen so far
        if (matchResult != null && matchResult.score > (targetMatchScore ?: 0)) {
            targetMatchScore = matchResult.score
            targetMatchSignals = matchResult.matchedSignals
        }
    }
}
