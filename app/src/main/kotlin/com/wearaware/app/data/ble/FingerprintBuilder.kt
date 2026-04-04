package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.DeviceFingerprint
import com.wearaware.app.domain.model.RawScanResult
import com.wearaware.app.domain.rules.CompanyIdMap
import java.security.MessageDigest

/**
 * PURPOSE: Derives a content-based DeviceFingerprint from a RawScanResult.
 *   fingerprintId is stable across MAC rotations — it depends only on advertising content.
 * NOTES: Uses SHA-256 from java.security (standard Java — no Android dependency).
 *   The hash input is: sorted manufacturer IDs + their hex data + sorted service UUIDs + normalized name.
 *   First 16 hex chars of the hash are used as the ID (64-bit collision space, sufficient for session use).
 */
fun RawScanResult.toFingerprint(): DeviceFingerprint {
    val stableInput = buildString {
        manufacturerData.keys.sorted().forEach { id ->
            append(id.toString(16).padStart(4, '0'))
            manufacturerData[id]?.let { append(it.toHexString()) }
        }
        serviceUuids.sorted().forEach { append(it) }
        advertisedName?.lowercase()?.trim()?.let { append(it) }
    }

    val hashBytes = MessageDigest.getInstance("SHA-256").digest(stableInput.toByteArray(Charsets.UTF_8))
    val fingerprintId = hashBytes.toHexString().take(16)

    return DeviceFingerprint(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerData.keys.sorted(),
        manufacturerNames = CompanyIdMap.namesFor(manufacturerData.keys),
        manufacturerDataHex = manufacturerData.mapValues { it.value.toHexString() },
        serviceUuids = serviceUuids,
        normalizedName = advertisedName?.lowercase()?.trim(),
        txPower = txPowerLevel
    )
}

/** Converts a ByteArray to a lowercase hex string. */
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
