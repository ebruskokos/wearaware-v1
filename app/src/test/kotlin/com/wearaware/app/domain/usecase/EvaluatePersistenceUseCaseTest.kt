package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvaluatePersistenceUseCaseTest {

    private lateinit var useCase: EvaluatePersistenceUseCase

    @Before
    fun setUp() {
        useCase = EvaluatePersistenceUseCase()
    }

    private fun makeDevice(
        id: String = "device-1",
        visibility: VisibilityState = VisibilityState.DETECTED_NOW,
        seenDurationMs: Long = 65_000L,
        isWearableCandidate: Boolean = true,
        confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
        proximity: ProximityLabel = ProximityLabel.NEARBY
    ): ObservedDevice {
        val now = System.currentTimeMillis()
        return ObservedDevice(
            id = id,
            advertisedName = "Ray-Ban",
            rawRssi = -68,
            averagedRssi = -68,
            proximityLabel = proximity,
            visibilityState = visibility,
            firstSeenAt = now - seenDurationMs,
            lastSeenAt = now,
            seenCount = 20,
            classification = ClassificationResult(
                matchedRuleId = "meta_rayban_v1",
                ruleVersion = "1.0.0",
                category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
                displayLabel = "Camera-capable wearable detected nearby",
                confidence = confidence,
                isWearableCandidate = isWearableCandidate,
                evaluationNotes = "manufacturer ID matched"
            ),
            persistenceAlert = null
        )
    }

    @Test
    fun `returns alert when all conditions are met`() {
        val result = useCase(makeDevice(), emptyMap())
        assertNotNull(result)
        assertEquals(PersistenceAlertType.DEVICE_REMAINED_NEARBY, result?.alertType)
        assertEquals("device-1", result?.deviceId)
    }

    @Test
    fun `returns null when visibility is SIGNAL_LOST`() {
        assertNull(useCase(makeDevice(visibility = VisibilityState.SIGNAL_LOST), emptyMap()))
    }

    @Test
    fun `returns null when seenDuration is below threshold`() {
        assertNull(useCase(makeDevice(seenDurationMs = 30_000L), emptyMap()))
    }

    @Test
    fun `returns null when at exactly the threshold (not yet exceeded)`() {
        assertNull(useCase(makeDevice(seenDurationMs = 59_999L), emptyMap()))
    }

    @Test
    fun `returns alert when seenDuration exactly equals threshold`() {
        assertNotNull(useCase(makeDevice(seenDurationMs = 60_000L), emptyMap()))
    }

    @Test
    fun `returns null when isWearableCandidate is false`() {
        assertNull(useCase(makeDevice(isWearableCandidate = false), emptyMap()))
    }

    @Test
    fun `returns null when confidence is LOW`() {
        assertNull(useCase(makeDevice(confidence = ConfidenceLevel.LOW), emptyMap()))
    }

    @Test
    fun `returns alert when confidence is MEDIUM`() {
        assertNotNull(useCase(makeDevice(confidence = ConfidenceLevel.MEDIUM), emptyMap()))
    }

    @Test
    fun `returns null when proximity is WEAK`() {
        assertNull(useCase(makeDevice(proximity = ProximityLabel.WEAK), emptyMap()))
    }

    @Test
    fun `returns null when proximity is UNKNOWN`() {
        assertNull(useCase(makeDevice(proximity = ProximityLabel.UNKNOWN), emptyMap()))
    }

    @Test
    fun `returns alert when proximity is VERY_CLOSE`() {
        assertNotNull(useCase(makeDevice(proximity = ProximityLabel.VERY_CLOSE), emptyMap()))
    }

    @Test
    fun `returns null when within cooldown window`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val recentAlert = mapOf(key to System.currentTimeMillis() - 60_000L)
        assertNull(useCase(makeDevice(), recentAlert))
    }

    @Test
    fun `returns alert when cooldown has expired`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val expiredAlert = mapOf(key to System.currentTimeMillis() - 130_000L)
        assertNotNull(useCase(makeDevice(), expiredAlert))
    }

    @Test
    fun `alert for device-2 is not blocked by cooldown for device-1`() {
        val key = "device-1:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val device1Cooldown = mapOf(key to System.currentTimeMillis() - 10_000L)
        assertNotNull(useCase(makeDevice(id = "device-2"), device1Cooldown))
    }
}
