package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanLogMapperTest {

    private fun makeDevice(
        id: String = "device-1",
        name: String? = "Ray-Ban",
        rawRssi: Int = -62,
        avgRssi: Int = -65,
        proximity: ProximityLabel = ProximityLabel.STRONG,
        visibility: VisibilityState = VisibilityState.DETECTED_NOW,
        ruleId: String? = "meta_rayban_v1",
        ruleVersion: String? = "1.0.0",
        category: DeviceCategory = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
        notes: String? = "manufacturer ID matched"
    ) = ObservedDevice(
        id = id,
        advertisedName = name,
        rawRssi = rawRssi,
        averagedRssi = avgRssi,
        proximityLabel = proximity,
        visibilityState = visibility,
        firstSeenAt = 1000L,
        lastSeenAt = 2000L,
        seenCount = 5,
        classification = ClassificationResult(
            matchedRuleId = ruleId,
            ruleVersion = ruleVersion,
            category = category,
            displayLabel = "Camera-capable wearable detected nearby",
            confidence = confidence,
            isWearableCandidate = true,
            evaluationNotes = notes
        ),
        persistenceAlert = null
    )

    @Test
    fun `toScanLogEntity maps all fields correctly`() {
        val device = makeDevice()
        val entity = device.toScanLogEntity()

        assertEquals("device-1", entity.deviceId)
        assertEquals("Ray-Ban", entity.advertisedName)
        assertEquals(-62, entity.rawRssi)
        assertEquals(-65, entity.averagedRssi)
        assertEquals("STRONG", entity.proximityLabel)
        assertEquals("DETECTED_NOW", entity.visibilityState)
        assertEquals("meta_rayban_v1", entity.matchedRuleId)
        assertEquals("1.0.0", entity.ruleVersion)
        assertEquals("CAMERA_CAPABLE_WEARABLE", entity.category)
        assertEquals("HIGH", entity.confidence)
        assertEquals("manufacturer ID matched", entity.evaluationNotes)
    }

    @Test
    fun `toScanLogEntity handles null optional fields`() {
        val device = makeDevice(name = null, ruleId = null, ruleVersion = null, notes = null)
        val entity = device.toScanLogEntity()
        assertNull(entity.advertisedName)
        assertNull(entity.matchedRuleId)
        assertNull(entity.ruleVersion)
        assertNull(entity.evaluationNotes)
    }

    @Test
    fun `toDomain maps entity back to ScanLogEntry correctly`() {
        val entity = ScanLogEntity(
            id = 42,
            timestamp = 9999L,
            deviceId = "device-1",
            advertisedName = "Ray-Ban",
            rawRssi = -62,
            averagedRssi = -65,
            proximityLabel = "STRONG",
            visibilityState = "DETECTED_NOW",
            matchedRuleId = "meta_rayban_v1",
            ruleVersion = "1.0.0",
            category = "CAMERA_CAPABLE_WEARABLE",
            confidence = "HIGH",
            evaluationNotes = "manufacturer ID matched"
        )
        val entry = entity.toDomain()

        assertEquals(42L, entry.id)
        assertEquals(9999L, entry.timestamp)
        assertEquals("device-1", entry.deviceId)
        assertEquals("Ray-Ban", entry.advertisedName)
        assertEquals("STRONG", entry.proximityLabel)
        assertEquals("meta_rayban_v1", entry.matchedRuleId)
    }

    @Test
    fun `toScanLogEntity-toDomain round-trip preserves all fields`() {
        val device = makeDevice()
        val entity = device.toScanLogEntity()
        val entry = entity.toDomain()

        assertEquals(device.id, entry.deviceId)
        assertEquals(device.advertisedName, entry.advertisedName)
        assertEquals(device.rawRssi, entry.rawRssi)
        assertEquals(device.averagedRssi, entry.averagedRssi)
        assertEquals(device.proximityLabel.name, entry.proximityLabel)
        assertEquals(device.visibilityState.name, entry.visibilityState)
        assertEquals(device.classification.matchedRuleId, entry.matchedRuleId)
        assertEquals(device.classification.ruleVersion, entry.ruleVersion)
        assertEquals(device.classification.category.name, entry.category)
        assertEquals(device.classification.confidence.name, entry.confidence)
    }
}
