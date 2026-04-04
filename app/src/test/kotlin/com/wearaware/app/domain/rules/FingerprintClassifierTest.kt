package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.RulesData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FingerprintClassifierTest {

    private val metadata = RuleSetMetadata(version = "1.0.0", hash = "test-hash")

    private val metaRule = FingerprintRule(
        ruleId = "meta_rayban_v1",
        enabled = true,
        priority = 100,
        minScore = 4,
        manufacturerIds = listOf(117), // 0x0075
        namePatterns = listOf("Ray-Ban", "Meta", "Aria"),
        serviceUuids = emptyList(),
        deviceTypeHint = "smart_glasses",
        category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        displayLabel = "Camera-capable wearable detected nearby",
        confidence = ConfidenceLevel.HIGH,
        isWearableCandidate = true
    )

    private val snapRule = FingerprintRule(
        ruleId = "snapchat_spectacles_v1",
        enabled = true,
        priority = 90,
        minScore = 2,
        manufacturerIds = emptyList(),
        namePatterns = listOf("Spectacles", "Snap"),
        serviceUuids = emptyList(),
        deviceTypeHint = "smart_glasses",
        category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
        displayLabel = "Camera-capable wearable detected nearby",
        confidence = ConfidenceLevel.MEDIUM,
        isWearableCandidate = true
    )

    private lateinit var classifier: FingerprintClassifier

    @Before
    fun setUp() {
        classifier = FingerprintClassifier(RulesData(metadata, listOf(metaRule, snapRule)))
    }

    private fun makeScan(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String? = null,
        manufacturerIds: List<Int> = emptyList(),
        serviceUuids: List<String> = emptyList()
    ) = RawScanResult(
        address = address,
        advertisedName = name,
        bluetoothDeviceName = null,
        rssi = -60,
        manufacturerData = manufacturerIds.associateWith { byteArrayOf() },
        serviceUuids = serviceUuids,
        serviceData = emptyMap(),
        txPowerLevel = null,
        advertisingFlags = null,
        isConnectable = false,
        deviceType = 0,
        bondState = 10,
        timestampMs = 0L
    )

    @Test
    fun `classifies Meta glasses by manufacturer ID alone (score = 4, minScore = 4)`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
        assertEquals("meta_rayban_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
        assertTrue(result.isWearableCandidate)
    }

    @Test
    fun `name-only Ray-Ban match does not reach Meta minScore of 4 - falls back to UNKNOWN`() {
        // "Ray-Ban" matches meta name pattern (+2) but meta minScore=4, so meta doesn't match.
        // Snap rule has no "Ray-Ban" pattern. Generic watch has no match.
        // Result: UNKNOWN_BLE_DEVICE
        val result = classifier.classify(makeScan(name = "Ray-Ban Smart Glasses"))
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
        assertNull(result.matchedRuleId)
    }

    @Test
    fun `name-only match reaches Snap minScore of 2`() {
        val result = classifier.classify(makeScan(name = "Spectacles v3"))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
        assertEquals("snapchat_spectacles_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.MEDIUM, result.confidence)
    }

    @Test
    fun `manufacturer + name match scores highest for Meta rule`() {
        val result = classifier.classify(
            makeScan(name = "Ray-Ban Meta Glasses", manufacturerIds = listOf(117))
        )
        assertEquals("meta_rayban_v1", result.matchedRuleId)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun `returns UNKNOWN_BLE_DEVICE for unmatched device`() {
        val result = classifier.classify(makeScan(name = "JBL Speaker", manufacturerIds = listOf(999)))
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
        assertNull(result.matchedRuleId)
        assertFalse(result.isWearableCandidate)
    }

    @Test
    fun `classification is deterministic - same input produces same output`() {
        val scan = makeScan(name = "Ray-Ban", manufacturerIds = listOf(117))
        val result1 = classifier.classify(scan)
        val result2 = classifier.classify(scan)
        assertEquals(result1.matchedRuleId, result2.matchedRuleId)
        assertEquals(result1.category, result2.category)
        assertEquals(result1.confidence, result2.confidence)
    }

    @Test
    fun `name matching is case insensitive`() {
        val result = classifier.classify(makeScan(
            name = "RAY-BAN SMART GLASSES",
            manufacturerIds = listOf(117)
        ))
        assertEquals(DeviceCategory.CAMERA_CAPABLE_WEARABLE, result.category)
    }

    @Test
    fun `disabled rule is never matched`() {
        val disabledRule = metaRule.copy(enabled = false)
        val classifierWithDisabled = FingerprintClassifier(
            RulesData(metadata, listOf(disabledRule))
        )
        val result = classifierWithDisabled.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals(DeviceCategory.UNKNOWN_BLE_DEVICE, result.category)
    }

    @Test
    fun `higher priority rule wins on equal score`() {
        val lowPriorityMeta = metaRule.copy(priority = 10, minScore = 2)
        val highPrioritySnap = snapRule.copy(priority = 200, minScore = 2)
        val classifier2 = FingerprintClassifier(
            RulesData(metadata, listOf(lowPriorityMeta, highPrioritySnap))
        )
        // Both match name (score=2), snap has higher priority
        val result = classifier2.classify(makeScan(name = "Snap Ray-Ban"))
        assertEquals("snapchat_spectacles_v1", result.matchedRuleId)
    }

    @Test
    fun `evaluationNotes are non-null when manufacturer matches`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertNotNull(result.evaluationNotes)
        assertTrue(result.evaluationNotes!!.contains("manufacturer", ignoreCase = true))
    }

    @Test
    fun `ruleVersion matches metadata version`() {
        val result = classifier.classify(makeScan(manufacturerIds = listOf(117)))
        assertEquals("1.0.0", result.ruleVersion)
    }
}
