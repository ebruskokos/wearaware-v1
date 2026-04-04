package com.wearaware.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedDeviceTest {

    private fun makeDevice(firstSeenAt: Long, lastSeenAt: Long): ObservedDevice {
        val classification = ClassificationResult(
            matchedRuleId = null,
            ruleVersion = null,
            category = DeviceCategory.UNKNOWN_BLE_DEVICE,
            displayLabel = "Unknown BLE device",
            confidence = ConfidenceLevel.LOW,
            isWearableCandidate = false,
            evaluationNotes = null
        )
        return ObservedDevice(
            id = "test-id",
            advertisedName = null,
            rawRssi = -70,
            averagedRssi = -70,
            proximityLabel = ProximityLabel.NEARBY,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            seenCount = 1,
            classification = classification,
            persistenceAlert = null
        )
    }

    @Test
    fun `seenDurationMs returns difference between lastSeenAt and firstSeenAt`() {
        val device = makeDevice(firstSeenAt = 1000L, lastSeenAt = 61_000L)
        assertEquals(60_000L, device.seenDurationMs)
    }

    @Test
    fun `seenDurationMs is zero when first and last are equal`() {
        val device = makeDevice(firstSeenAt = 5000L, lastSeenAt = 5000L)
        assertEquals(0L, device.seenDurationMs)
    }

    @Test
    fun `seenDurationMs reflects large values correctly`() {
        val device = makeDevice(firstSeenAt = 0L, lastSeenAt = 120_000L)
        assertEquals(120_000L, device.seenDurationMs)
    }
}
