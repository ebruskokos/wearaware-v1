package com.wearaware.app.data.ble

import android.bluetooth.le.ScanResult
import com.wearaware.app.domain.model.RawScanResult

/**
 * PURPOSE: Maps Android's ScanResult to the domain RawScanResult.
 *   This is the ONLY place in the app that reads android.bluetooth.le.ScanResult.
 * LIMITATIONS: scanRecord may be null for some BLE devices; all nullable fields
 *   are handled safely. txPowerLevel uses Int.MIN_VALUE as a sentinel for "not present"
 *   in the Android API — we normalize this to null.
 * NOTES: manufacturerData is a SparseArray in Android. We convert it to Map<Int, ByteArray>
 *   to keep domain models free of Android imports.
 */
fun ScanResult.toRawScanResult(): RawScanResult {
    val record = scanRecord
    val manufacturerData = mutableMapOf<Int, ByteArray>()
    record?.manufacturerSpecificData?.let { sparseArray ->
        for (i in 0 until sparseArray.size()) {
            manufacturerData[sparseArray.keyAt(i)] = sparseArray.valueAt(i) ?: byteArrayOf()
        }
    }
    return RawScanResult(
        address = device.address,
        advertisedName = record?.deviceName ?: device.name,
        rssi = rssi,
        manufacturerData = manufacturerData,
        serviceUuids = record?.serviceUuids?.map { it.uuid.toString().lowercase() }
            ?: emptyList(),
        txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        timestampMs = System.currentTimeMillis()
    )
}
