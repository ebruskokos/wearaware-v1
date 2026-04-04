package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.RawScanResult
import org.junit.Assert.*
import org.junit.Test

class FingerprintBuilderTest {

    private fun makeRaw(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String? = null,
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
        serviceUuids: List<String> = emptyList()
    ) = RawScanResult(
        address = address,
        advertisedName = name,
        bluetoothDeviceName = null,
        rssi = -70,
        manufacturerData = manufacturerData,
        serviceUuids = serviceUuids,
        serviceData = emptyMap(),
        txPowerLevel = null,
        advertisingFlags = null,
        isConnectable = false,
        deviceType = 2,
        bondState = 10,
        timestampMs = System.currentTimeMillis()
    )

    @Test
    fun `same input produces same fingerprint ID`() {
        val raw1 = makeRaw(name = "Ray-Ban", manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        val raw2 = makeRaw(name = "Ray-Ban", manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        assertEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }

    @Test
    fun `different manufacturer data produces different fingerprint ID`() {
        val raw1 = makeRaw(manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        val raw2 = makeRaw(manufacturerData = mapOf(0x0075 to byteArrayOf(4, 5, 6)))
        assertNotEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }

    @Test
    fun `manufacturer names resolved via CompanyIdMap`() {
        val raw = makeRaw(manufacturerData = mapOf(0x004C to byteArrayOf(), 0x0075 to byteArrayOf()))
        val fp = raw.toFingerprint()
        assertTrue(fp.manufacturerNames.contains("Apple"))
        assertTrue(fp.manufacturerNames.contains("Meta"))
    }

    @Test
    fun `different MAC address but same content gives same fingerprint ID`() {
        val raw1 = makeRaw(address = "AA:BB:CC:DD:EE:FF", name = "TestDevice",
            manufacturerData = mapOf(0x004C to byteArrayOf(1)))
        val raw2 = makeRaw(address = "11:22:33:44:55:66", name = "TestDevice",
            manufacturerData = mapOf(0x004C to byteArrayOf(1)))
        assertEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }
}
