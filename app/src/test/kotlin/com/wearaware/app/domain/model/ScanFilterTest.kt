package com.wearaware.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ScanFilterTest {

    private fun makeDevice(
        id: String = "dev1",
        name: String? = null,
        category: DeviceCategory = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames: List<String> = emptyList(),
        proximity: ProximityLabel = ProximityLabel.WEAK
    ): ObservedDevice {
        val now = System.currentTimeMillis()
        return ObservedDevice(
            id = id,
            advertisedName = name,
            rawRssi = -80,
            averagedRssi = -80,
            proximityLabel = proximity,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now,
            lastSeenAt = now,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = null,
                ruleVersion = null,
                category = category,
                displayLabel = category.name,
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = null
            ),
            persistenceAlert = null,
            companyNames = companyNames
        )
    }

    @Test
    fun `ALL filter keeps every device`() {
        val devices = listOf(
            makeDevice("a", companyNames = listOf("Apple")),
            makeDevice("b", category = DeviceCategory.SMART_GLASSES),
            makeDevice("c")
        )
        val result = devices.filter { ScanFilter.ALL.matches(it) }
        assertEquals(3, result.size)
    }

    @Test
    fun `GLASSES_CANDIDATES keeps only smart glasses and camera-capable`() {
        val devices = listOf(
            makeDevice("a", category = DeviceCategory.SMART_GLASSES),
            makeDevice("b", category = DeviceCategory.CAMERA_CAPABLE_WEARABLE),
            makeDevice("c", category = DeviceCategory.UNKNOWN_BLE_DEVICE),
            makeDevice("d", category = DeviceCategory.SMARTWATCH)
        )
        val result = devices.filter { ScanFilter.GLASSES_CANDIDATES.matches(it) }
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `META_DEVICES keeps only devices with Meta in companyNames`() {
        val devices = listOf(
            makeDevice("a", companyNames = listOf("Meta")),
            makeDevice("b", companyNames = listOf("Apple")),
            makeDevice("c", companyNames = listOf("Meta", "Apple"))
        )
        val result = devices.filter { ScanFilter.META_DEVICES.matches(it) }
        assertEquals(listOf("a", "c"), result.map { it.id })
    }

    @Test
    fun `HIDE_APPLE removes devices with Apple in companyNames`() {
        val devices = listOf(
            makeDevice("a", companyNames = listOf("Apple")),
            makeDevice("b", companyNames = listOf("Meta")),
            makeDevice("c")
        )
        val result = devices.filter { ScanFilter.HIDE_APPLE.matches(it) }
        assertEquals(listOf("b", "c"), result.map { it.id })
    }

    @Test
    fun `UNKNOWN_ONLY keeps only UNKNOWN_BLE_DEVICE category`() {
        val devices = listOf(
            makeDevice("a", category = DeviceCategory.UNKNOWN_BLE_DEVICE),
            makeDevice("b", category = DeviceCategory.SMARTWATCH)
        )
        val result = devices.filter { ScanFilter.UNKNOWN_ONLY.matches(it) }
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `STRONG_SIGNAL keeps only NEARBY, STRONG, and VERY_CLOSE devices`() {
        val devices = listOf(
            makeDevice("a", proximity = ProximityLabel.VERY_CLOSE),
            makeDevice("b", proximity = ProximityLabel.STRONG),
            makeDevice("c", proximity = ProximityLabel.NEARBY),
            makeDevice("d", proximity = ProximityLabel.WEAK),
            makeDevice("e", proximity = ProximityLabel.UNKNOWN)
        )
        val result = devices.filter { ScanFilter.STRONG_SIGNAL.matches(it) }
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }
}
