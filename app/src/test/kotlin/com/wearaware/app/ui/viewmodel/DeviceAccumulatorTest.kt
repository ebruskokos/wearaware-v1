package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DeviceAccumulatorTest {

    private fun makeAcc(
        fingerprintId: String = "fp1",
        manufacturerIds: List<Int> = emptyList(),
        serviceUuids: List<String> = emptyList(),
        category: DeviceCategory = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames: List<String> = emptyList(),
        targetMatchScore: Int? = null,
        targetMatchSignals: List<String> = emptyList()
    ) = DeviceAccumulator(
        fingerprintId = fingerprintId,
        advertisedName = null,
        macAddress = null,
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = null,
        serviceUuids = serviceUuids,
        category = category,
        companyNames = companyNames,
        firstSeenInCapture = 1000L,
        lastSeenInCapture = 1000L,
        peakRssi = -70,
        runningRssiSum = -70L,
        readingCount = 1,
        seenCount = 1,
        targetMatchScore = targetMatchScore,
        targetMatchSignals = targetMatchSignals
    )

    private fun makeObservedDevice(
        id: String = "fp1",
        manufacturerIds: List<Int> = emptyList(),
        manufacturerDataHex: Map<Int, String> = emptyMap(),
        serviceUuids: List<String> = emptyList(),
        category: DeviceCategory = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames: List<String> = emptyList(),
        rssi: Int = -70
    ): ObservedDevice {
        val fingerprint = if (manufacturerIds.isNotEmpty() || serviceUuids.isNotEmpty() || manufacturerDataHex.isNotEmpty()) {
            DeviceFingerprint(
                fingerprintId = id,
                manufacturerIds = manufacturerIds,
                manufacturerNames = companyNames,
                manufacturerDataHex = manufacturerDataHex,
                serviceUuids = serviceUuids,
                normalizedName = null,
                txPower = null
            )
        } else null
        return ObservedDevice(
            id = id,
            advertisedName = null,
            rawRssi = rssi,
            averagedRssi = rssi,
            proximityLabel = ProximityLabel.UNKNOWN,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = null,
                ruleVersion = null,
                category = category,
                displayLabel = "Unknown",
                confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false,
                evaluationNotes = null
            ),
            persistenceAlert = null,
            macAddress = null,
            fingerprint = fingerprint,
            companyNames = companyNames
        )
    }

    @Test
    fun `device initially seen with empty manufacturerIds is updated from richer later observation`() {
        // First scan tick: no manufacturer data (fingerprint not yet resolved)
        val acc = makeAcc(manufacturerIds = emptyList(), companyNames = emptyList())

        // Later tick: same fingerprintId, now advertising manufacturer data
        val update = makeObservedDevice(
            manufacturerIds = listOf(0x0075),
            companyNames = listOf("Meta")
        )

        acc.updateFrom(update, matchResult = null, now = 2000L)

        assertEquals(
            "manufacturerIds should be updated from the richer later observation",
            listOf(0x0075), acc.manufacturerIds
        )
        assertEquals(
            "companyNames should be updated alongside manufacturerIds",
            listOf("Meta"), acc.companyNames
        )
    }

    @Test
    fun `device initially seen with no classification is updated when later tick provides one`() {
        // First scan tick: no rule matched, device is UNKNOWN_BLE_DEVICE
        val acc = makeAcc(category = DeviceCategory.UNKNOWN_BLE_DEVICE)

        // Later tick: fingerprint classifier now identifies the device as smart glasses
        val update = makeObservedDevice(category = DeviceCategory.SMART_GLASSES)

        acc.updateFrom(update, matchResult = null, now = 2000L)

        assertEquals(
            "category should be upgraded from UNKNOWN_BLE_DEVICE to the richer classification",
            DeviceCategory.SMART_GLASSES, acc.category
        )
    }

    @Test
    fun `device initially seen with empty target signals is updated when later tick has higher match score`() {
        // First scan tick: MatchTargetDeviceUseCase produced no result
        val acc = makeAcc(targetMatchScore = null, targetMatchSignals = emptyList())

        // Later tick: richer advertising data now triggers a strong match
        val richMatchResult = TargetMatchResult(
            deviceId = "fp1",
            score = 9,
            confidence = MatchConfidence.HIGH,
            matchedSignals = listOf("Meta manufacturer match (0x0075)", "Classification: SMART_GLASSES"),
            isTopCandidate = true
        )

        acc.updateFrom(makeObservedDevice(), matchResult = richMatchResult, now = 2000L)

        assertEquals(
            "targetMatchScore should be updated to the higher score from the later tick",
            9, acc.targetMatchScore
        )
        assertEquals(
            "targetMatchSignals should reflect the signals from the higher-score tick",
            listOf("Meta manufacturer match (0x0075)", "Classification: SMART_GLASSES"),
            acc.targetMatchSignals
        )
    }
}
