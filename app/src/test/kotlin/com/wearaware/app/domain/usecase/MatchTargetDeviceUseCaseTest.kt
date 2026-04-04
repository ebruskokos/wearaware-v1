package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchTargetDeviceUseCaseTest {

    private val useCase = MatchTargetDeviceUseCase()
    private val profile = DefaultTargetProfile.WAYFARER_00ZS

    private fun makeDevice(
        id: String = "abc123",
        name: String? = null,
        category: DeviceCategory = DeviceCategory.UNKNOWN_BLE_DEVICE,
        matchedRuleId: String? = null,
        proximity: ProximityLabel = ProximityLabel.UNKNOWN,
        seenDurationMs: Long = 0L
    ): ObservedDevice {
        val now = System.currentTimeMillis()
        return ObservedDevice(
            id = id,
            advertisedName = name,
            rawRssi = -70,
            averagedRssi = -70,
            proximityLabel = proximity,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now - seenDurationMs,
            lastSeenAt = now,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = matchedRuleId,
                ruleVersion = if (matchedRuleId != null) "1.0.0" else null,
                category = category,
                displayLabel = category.name,
                confidence = if (matchedRuleId != null) ConfidenceLevel.HIGH else ConfidenceLevel.LOW,
                isWearableCandidate = category == DeviceCategory.CAMERA_CAPABLE_WEARABLE ||
                        category == DeviceCategory.SMART_GLASSES,
                evaluationNotes = null
            ),
            persistenceAlert = null
        )
    }

    @Test
    fun `exact name match gives HIGH confidence`() {
        val device = makeDevice(name = "Wayfarer 00ZS")
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 9, was ${result.score}", result.score >= 9)
        assertEquals(MatchConfidence.HIGH, result.confidence)
        assertTrue(result.matchedSignals.any { it.contains("Exact name match") })
    }

    @Test
    fun `partial name match containing model hint gives MEDIUM or higher confidence`() {
        val device = makeDevice(name = "Wayfarer 01AB")
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 6, was ${result.score}", result.score >= 6)
        assertTrue(result.matchedSignals.any { it.contains("Partial name match") })
    }

    @Test
    fun `manufacturer rule match alone gives at least MEDIUM confidence`() {
        val device = makeDevice(
            category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
            matchedRuleId = "meta_rayban_v1"
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 6, was ${result.score}", result.score >= 6)
        assertTrue(result.matchedSignals.any { it.contains("Manufacturer match") })
    }

    @Test
    fun `no match signals gives NONE confidence and not top candidate`() {
        val device = makeDevice(name = "Samsung TV Remote")
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertEquals(MatchConfidence.NONE, result.confidence)
        assertFalse(result.isTopCandidate)
    }

    @Test
    fun `top candidate is the device with highest score`() {
        val weak = makeDevice("id1", name = "Generic BLE Device")
        val strong = makeDevice("id2", name = "Wayfarer 00ZS")
        val results = useCase(listOf(weak, strong), profile)
        assertFalse(results["id1"]!!.isTopCandidate)
        assertTrue(results["id2"]!!.isTopCandidate)
    }

    @Test
    fun `empty device list returns empty map`() {
        assertTrue(useCase(emptyList(), profile).isEmpty())
    }

    @Test
    fun `persistence bonus added when device seen 30 seconds or more`() {
        val device = makeDevice(
            category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
            matchedRuleId = "meta_rayban_v1",
            seenDurationMs = 35_000L
        )
        val result = useCase(listOf(device), profile)[device.id]!!
        assertTrue(result.matchedSignals.any { it.contains("Persistent") })
    }
}
