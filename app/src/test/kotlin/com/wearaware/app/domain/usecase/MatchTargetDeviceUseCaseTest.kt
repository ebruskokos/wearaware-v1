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

    @Test
    fun `generic BLE device with no identity signals gets NONE even when it is the only device`() {
        // LEDBLE-0038ED type: unknown manufacturer, UNKNOWN_BLE_DEVICE, no Wayfarer name
        val device = makeDevice(
            id = "ledble1",
            name = "LEDBLE-0038ED",
            category = DeviceCategory.UNKNOWN_BLE_DEVICE,
            seenDurationMs = 120_000L  // persistence bonus should be irrelevant
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertEquals("No identity signal — must be NONE", MatchConfidence.NONE, result.confidence)
        assertEquals("Score must be 0 for ineligible device", 0, result.score)
        assertFalse("Ineligible device must not be top candidate", result.isTopCandidate)
    }

    @Test
    fun `LOW confidence device is never marked as top candidate`() {
        // SMART_GLASSES classification alone = 3 pts = LOW
        val device = makeDevice(
            id = "sg1",
            category = DeviceCategory.SMART_GLASSES
            // no name, no Meta rule → eligible but score = 3 = LOW
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertEquals("SMART_GLASSES only should give LOW", MatchConfidence.LOW, result.confidence)
        assertFalse("LOW confidence device must not become top candidate", result.isTopCandidate)
    }

    @Test
    fun `persistence and UUID alone without identity signal are not enough to become a candidate`() {
        // A device with only bonus signals — no name hint, no Meta, no wearable class
        val device = makeDevice(
            id = "generic2",
            category = DeviceCategory.UNKNOWN_BLE_DEVICE,
            seenDurationMs = 90_000L
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertEquals(MatchConfidence.NONE, result.confidence)
        assertTrue("No signals should be recorded for ineligible device", result.matchedSignals.isEmpty())
        assertFalse(result.isTopCandidate)
    }

    @Test
    fun `Meta company name in companyNames satisfies eligibility and adds to score`() {
        val now = System.currentTimeMillis()
        val device = ObservedDevice(
            id = "meta_by_company_id",
            advertisedName = null,
            rawRssi = -60,
            averagedRssi = -60,
            proximityLabel = ProximityLabel.NEARBY,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now,
            lastSeenAt = now,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = null,
                ruleVersion = null,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = "Unknown",
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = null
            ),
            persistenceAlert = null,
            companyNames = listOf("Meta")
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("Meta company ID should contribute +3 to score", result.score >= 3)
        assertTrue(result.matchedSignals.any { it.contains("Manufacturer match") })
    }
}
