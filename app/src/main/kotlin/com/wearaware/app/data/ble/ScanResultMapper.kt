package com.wearaware.app.data.ble

import android.bluetooth.le.ScanResult
import android.os.Build
import com.wearaware.app.domain.model.RawScanResult

/**
 * PURPOSE: Maps Android's ScanResult to the domain RawScanResult.
 *   This is the ONLY place in the app that reads android.bluetooth.le.ScanResult.
 *   Extracts ALL available BLE fields for maximum identification fidelity.
 * NOTES: isConnectable() requires API 26; guarded with Build.VERSION check.
 *   serviceData keys are ParcelUuid — converted to lowercase UUID strings.
 *   deviceType and bondState are stable Android constants (int values).
 */
fun ScanResult.toRawScanResult(): RawScanResult {
    val record = scanRecord

    val manufacturerData = mutableMapOf<Int, ByteArray>()
    record?.manufacturerSpecificData?.let { sparse ->
        for (i in 0 until sparse.size()) {
            manufacturerData[sparse.keyAt(i)] = sparse.valueAt(i) ?: byteArrayOf()
        }
    }

    val serviceData = mutableMapOf<String, ByteArray>()
    record?.serviceData?.forEach { (uuid, data) ->
        serviceData[uuid.uuid.toString().lowercase()] = data ?: byteArrayOf()
    }

    val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        isConnectable
    } else {
        false
    }

    return RawScanResult(
        address = device.address,
        advertisedName = record?.deviceName ?: device.name,
        bluetoothDeviceName = device.name,
        rssi = rssi,
        manufacturerData = manufacturerData,
        serviceUuids = record?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList(),
        serviceData = serviceData,
        txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        advertisingFlags = record?.advertiseFlags?.takeIf { it >= 0 },
        isConnectable = connectable,
        deviceType = device.type,
        bondState = device.bondState,
        timestampMs = System.currentTimeMillis()
    )
}
