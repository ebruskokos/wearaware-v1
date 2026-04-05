package com.wearaware.app.data.mapper

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CaptureMapperTest {

    private fun makeDevice(
        id: String = "fp1",
        manufacturerIds: List<Int> = listOf(0x0075, 0x004c),
        serviceUuids: List<String> = listOf("0000fe2c-0000-1000-8000-00805f9b34fb"),
        companyNames: List<String> = listOf("Meta", "Apple")
    ): CapturedDevice = CapturedDevice(
        fingerprintId = id,
        advertisedName = "Test Device",
        macAddress = "AA:BB:CC:DD:EE:FF",
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = "0075:deadbeef",
        serviceUuids = serviceUuids,
        category = DeviceCategory.SMART_GLASSES,
        companyNames = companyNames,
        firstSeenInCapture = 1000L,
        lastSeenInCapture = 2000L,
        peakRssi = -55,
        averageRssi = -62,
        seenCount = 10,
        visibleAtStop = true,
        targetMatchScore = 9,
        targetMatchSignals = listOf("Exact name match", "Meta manufacturer")
    )

    @Test
    fun `CapturedDevice round-trips through entity and back`() {
        val device = makeDevice()
        val entity = device.toDeviceEntity("session-1")
        val restored = entity.toDomain()

        assertEquals(device.fingerprintId, restored.fingerprintId)
        assertEquals(device.advertisedName, restored.advertisedName)
        assertEquals(device.manufacturerIds, restored.manufacturerIds)
        assertEquals(device.serviceUuids, restored.serviceUuids)
        assertEquals(device.category, restored.category)
        assertEquals(device.companyNames, restored.companyNames)
        assertEquals(device.averageRssi, restored.averageRssi)
        assertEquals(device.seenCount, restored.seenCount)
        assertEquals(device.visibleAtStop, restored.visibleAtStop)
        assertEquals(device.targetMatchScore, restored.targetMatchScore)
        assertEquals(device.targetMatchSignals, restored.targetMatchSignals)
    }

    @Test
    fun `CaptureSession round-trips through entity and device entities`() {
        val device = makeDevice()
        val session = CaptureSession(
            id = "session-abc",
            type = CaptureType.BASELINE,
            startedAt = 1000L,
            stoppedAt = 5000L,
            devices = listOf(device)
        )
        val sessionEntity = session.toSessionEntity()
        val deviceEntities = session.devices.map { it.toDeviceEntity(session.id) }
        val restored = sessionEntity.toDomain(deviceEntities)

        assertEquals(session.id, restored.id)
        assertEquals(session.type, restored.type)
        assertEquals(session.startedAt, restored.startedAt)
        assertEquals(session.stoppedAt, restored.stoppedAt)
        assertEquals(1, restored.devices.size)
        assertEquals(device.fingerprintId, restored.devices[0].fingerprintId)
    }

    @Test
    fun `manufacturerIds are hex-encoded and decoded correctly`() {
        val device = makeDevice(manufacturerIds = listOf(0x0075, 0x004c))
        val entity = device.toDeviceEntity("s1")
        assertEquals("0075,004c", entity.manufacturerIds)
        val restored = entity.toDomain()
        assertEquals(listOf(0x0075, 0x004c), restored.manufacturerIds)
    }

    @Test
    fun `empty manufacturerIds round-trips as empty list`() {
        val device = makeDevice(manufacturerIds = emptyList())
        val entity = device.toDeviceEntity("s1")
        assertNull(entity.manufacturerIds)
        val restored = entity.toDomain()
        assertTrue(restored.manufacturerIds.isEmpty())
    }

    @Test
    fun `empty serviceUuids round-trips as empty list`() {
        val device = makeDevice(serviceUuids = emptyList())
        val entity = device.toDeviceEntity("s1")
        assertNull(entity.serviceUuids)
        val restored = entity.toDomain()
        assertTrue(restored.serviceUuids.isEmpty())
    }

    @Test
    fun `targetMatchSignals are semicolon-encoded and decoded correctly`() {
        val device = makeDevice()
        val entity = device.toDeviceEntity("s1")
        assertEquals("Exact name match;Meta manufacturer", entity.targetMatchSignals)
        val restored = entity.toDomain()
        assertEquals(listOf("Exact name match", "Meta manufacturer"), restored.targetMatchSignals)
    }
}
