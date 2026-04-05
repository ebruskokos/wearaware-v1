package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.BleDebugData
import org.junit.Assert.*
import org.junit.Test

class BleDebugDataMappingTest {

    @Test
    fun `serviceDataHex encodes byte arrays as lowercase hex strings`() {
        val serviceData = BleDebugData(
            serviceSolicitationUuids = emptyList(),
            serviceDataHex = mapOf(
                "0000fe95-0000-1000-8000-00805f9b34fb" to "deadbeef",
                "0000180a-0000-1000-8000-00805f9b34fb" to "cafe0102"
            ),
            rawScanBytesHex = null,
            isConnectable = true,
            advertisingFlags = 0x1A,
            timestampNanos = 123456789L,
            primaryPhy = 1,
            secondaryPhy = 0,
            advertisingSid = 255,
            periodicAdvertisingInterval = 0,
            deviceType = 2,
            bondState = 10
        )
        assertEquals("deadbeef", serviceData.serviceDataHex["0000fe95-0000-1000-8000-00805f9b34fb"])
        assertEquals("cafe0102", serviceData.serviceDataHex["0000180a-0000-1000-8000-00805f9b34fb"])
    }

    @Test
    fun `rawScanBytesHex is null when ScanRecord bytes are null`() {
        val debugData = BleDebugData(
            serviceSolicitationUuids = emptyList(),
            serviceDataHex = emptyMap(),
            rawScanBytesHex = null,
            isConnectable = false,
            advertisingFlags = null,
            timestampNanos = 0L,
            primaryPhy = 0,
            secondaryPhy = 0,
            advertisingSid = 255,
            periodicAdvertisingInterval = 0,
            deviceType = 0,
            bondState = 10
        )
        assertNull(debugData.rawScanBytesHex)
    }

    @Test
    fun `toHexString produces correct lowercase hex for known bytes`() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("deadbeef", bytes.toHexString())
    }

    @Test
    fun `toHexString returns empty string for empty ByteArray`() {
        assertEquals("", byteArrayOf().toHexString())
    }
}
