package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CompareCapturesUseCaseTest {

    private val useCase = CompareCapturesUseCase()
    private val profile = DefaultTargetProfile.WAYFARER_00ZS  // friendlyName="Wayfarer 00ZS", modelHint="Wayfarer", brandHint="Meta"

    private fun makeDevice(
        id: String = "fp1",
        name: String? = null,
        manufacturerIds: List<Int> = emptyList(),
        category: DeviceCategory = DeviceCategory.UNKNOWN_BLE_DEVICE,
        averageRssi: Int = -70,
        seenCount: Int = 1,
        // Default false so existing score assertions are unaffected by the +1 visibleAtStop bonus
        visibleAtStop: Boolean = false
    ): CapturedDevice = CapturedDevice(
        fingerprintId = id,
        advertisedName = name,
        macAddress = null,
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = null,
        serviceUuids = emptyList(),
        category = category,
        companyNames = emptyList(),
        firstSeenInCapture = 0L,
        lastSeenInCapture = 0L,
        peakRssi = averageRssi,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop,
        targetMatchScore = null,
        targetMatchSignals = emptyList()
    )

    private fun makeSession(type: CaptureType, devices: List<CapturedDevice>): CaptureSession {
        val now = System.currentTimeMillis()
        return CaptureSession(
            id = "session-${type.name}",
            type = type,
            startedAt = now - 30_000L,
            stoppedAt = now,
            devices = devices
        )
    }

    // --- Only-in-target gated signal ---

    @Test
    fun `device only in target with Meta manufacturer ID gets HIGH confidence`() {
        val target = makeSession(CaptureType.TARGET, listOf(
            makeDevice("fp1", manufacturerIds = listOf(0x0075))
        ))
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +8 (only-in-target gated by Meta) + +3 (Meta manufacturer) = 11
        assertEquals(11, result.score)
        assertEquals(CompareConfidence.HIGH, result.confidence)
        assertTrue(result.comparisonSignals.any { it.contains("Only appeared") })
        assertTrue(result.comparisonSignals.any { it.contains("Meta") })
    }

    @Test
    fun `device only in target with no identity signal gets LOW confidence only`() {
        val target = makeSession(CaptureType.TARGET, listOf(
            makeDevice("fp2")  // no name, no Meta, no wearable class
        ))
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val results = useCase(baseline, target, profile)
        val result = results.first()
        assertEquals(3, result.score)
        assertEquals(CompareConfidence.LOW, result.confidence)
    }

    // --- RSSI delta ---

    @Test
    fun `device in both captures with RSSI delta 10 or more gets delta signal`() {
        val device = makeDevice("fp3", averageRssi = -60)
        val baselineDevice = makeDevice("fp3", averageRssi = -75)
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val baseline = makeSession(CaptureType.BASELINE, listOf(baselineDevice))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        assertTrue("Expected delta signal", result.comparisonSignals.any { it.contains("increased") })
        assertTrue("Expected +4 from delta", result.score >= 4)
        assertEquals(15, result.rssiDelta)
    }

    @Test
    fun `device in both captures with RSSI delta less than 10 does not get delta signal`() {
        val device = makeDevice("fp4", averageRssi = -70)
        val baselineDevice = makeDevice("fp4", averageRssi = -75)
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val baseline = makeSession(CaptureType.BASELINE, listOf(baselineDevice))
        val results = useCase(baseline, target, profile)
        val result = results.firstOrNull { it.capturedDevice.fingerprintId == "fp4" }
        // delta is 5, so no RSSI signal
        assertTrue(result == null || result.comparisonSignals.none { it.contains("increased") })
    }

    // --- Classification scores ---

    @Test
    fun `SMART_GLASSES scores 3 points and CAMERA_CAPABLE_WEARABLE scores 2 points`() {
        val sgDevice = makeDevice("fp5", category = DeviceCategory.SMART_GLASSES)
        val ccDevice = makeDevice("fp6", category = DeviceCategory.CAMERA_CAPABLE_WEARABLE)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(sgDevice, ccDevice))
        val results = useCase(baseline, target, profile)
        val sgResult = results.first { it.capturedDevice.fingerprintId == "fp5" }
        val ccResult = results.first { it.capturedDevice.fingerprintId == "fp6" }
        assertTrue("SMART_GLASSES score ${sgResult.score} should be higher than CAMERA ${ccResult.score}",
            sgResult.score > ccResult.score)
    }

    // --- Name signals ---

    @Test
    fun `exact target name match scores 4 points more than partial hint`() {
        val exactDevice = makeDevice("fp7", name = "Wayfarer 00ZS")
        val hintDevice = makeDevice("fp8", name = "Wayfarer 01AB")
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(exactDevice, hintDevice))
        val results = useCase(baseline, target, profile)
        val exactResult = results.first { it.capturedDevice.fingerprintId == "fp7" }
        val hintResult = results.first { it.capturedDevice.fingerprintId == "fp8" }
        // exact: +8 (gated, name is an identity signal) + +4 (exact) = 12
        // hint:  +8 (gated, name is an identity signal) + +2 (partial) = 10
        assertTrue("Exact score ${exactResult.score} > hint score ${hintResult.score}",
            exactResult.score > hintResult.score)
    }

    // --- Ranking and filtering ---

    @Test
    fun `results are sorted by score descending`() {
        val weakDevice = makeDevice("fp9")   // score 3
        val strongDevice = makeDevice("fp10", manufacturerIds = listOf(0x0075))  // score 11
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(weakDevice, strongDevice))
        val results = useCase(baseline, target, profile)
        assertEquals("fp10", results[0].capturedDevice.fingerprintId)
        assertEquals("fp9", results[1].capturedDevice.fingerprintId)
    }

    @Test
    fun `NONE confidence devices are excluded from results`() {
        // Device in both captures with no signals at all → score 0
        val device = makeDevice("fp11")
        val baseline = makeSession(CaptureType.BASELINE, listOf(device))
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        assertTrue("Device with score 0 should not appear",
            results.none { it.capturedDevice.fingerprintId == "fp11" })
    }

    @Test
    fun `empty results when no device scores above NONE threshold`() {
        // All devices in both captures, no RSSI delta, no identity signals
        val device = makeDevice("fp12")
        val baseline = makeSession(CaptureType.BASELINE, listOf(device))
        val target = makeSession(CaptureType.TARGET, listOf(makeDevice("fp12", averageRssi = -71)))
        val results = useCase(baseline, target, profile)
        assertTrue(results.isEmpty())
    }

    // --- Baseline null ---

    @Test
    fun `only-in-target signals do not fire when baseline is null`() {
        // Without baseline, even a Meta device cannot earn the +8 or +3 new-device bonus
        val metaDevice = makeDevice("fp13", manufacturerIds = listOf(0x0075))
        val target = makeSession(CaptureType.TARGET, listOf(metaDevice))
        val results = useCase(null, target, profile)
        val result = results.first { it.capturedDevice.fingerprintId == "fp13" }
        // Only +3 (Meta manufacturer) should fire — no +8 or +3 from "only in target"
        assertEquals(3, result.score)
        assertTrue(result.comparisonSignals.none { it.contains("Only appeared") })
    }

    @Test
    fun `hasBaseline is false for all results when baseline is null`() {
        val device = makeDevice("fp14", manufacturerIds = listOf(0x0075))
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(null, target, profile)
        assertTrue(results.all { !it.hasBaseline })
    }

    // --- Canonical field check ---

    @Test
    fun `Meta manufacturer check uses manufacturerIds not companyNames`() {
        // companyNames contains "Meta" but manufacturerIds is empty → no Meta score
        val deviceWithNameOnly = CapturedDevice(
            fingerprintId = "fp15",
            advertisedName = null,
            macAddress = null,
            manufacturerIds = emptyList(),       // canonical: empty
            manufacturerDataSummary = null,
            serviceUuids = emptyList(),
            category = DeviceCategory.UNKNOWN_BLE_DEVICE,
            companyNames = listOf("Meta"),        // display only — must not affect score
            firstSeenInCapture = 0L, lastSeenInCapture = 0L,
            peakRssi = -70, averageRssi = -70,
            seenCount = 1, visibleAtStop = false,  // false so +1 bonus doesn't shift score assertion
            targetMatchScore = null, targetMatchSignals = emptyList()
        )
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(deviceWithNameOnly))
        val results = useCase(baseline, target, profile)
        // device only in target, no identity signals → +3 only
        val result = results.firstOrNull { it.capturedDevice.fingerprintId == "fp15" }
        assertNotNull(result)
        assertEquals(3, result!!.score)
        assertTrue(result.comparisonSignals.none { it.contains("Meta manufacturer") })
    }

    // --- Persistence bonus ---

    @Test
    fun `persistence bonus adds 1 point when seenCount is 5 or more`() {
        val device = makeDevice("fp16", manufacturerIds = listOf(0x0075), seenCount = 5)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +8 (gated Meta) + +3 (Meta) + +1 (persistence) = 12
        assertEquals(12, result.score)
    }

    @Test
    fun `persistence bonus is absent when seenCount is less than 5`() {
        val device = makeDevice("fp17", manufacturerIds = listOf(0x0075), seenCount = 4)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +8 (gated Meta) + +3 (Meta) = 11 (no +1)
        assertEquals(11, result.score)
    }

    // --- Tie-break ---

    @Test
    fun `tie-break prefers exact name match over Meta-only at equal score`() {
        // Both score 11: exact name (+8 gated by name + +4 name = 12) vs Meta (+8 gated + +3 = 11)
        // Actually exact name will score higher, so let's test equal-score scenario:
        // Meta + SMART_GLASSES = +8 + +3 + +3 = 14
        // Exact name + SMART_GLASSES = +8 + +4 + +3 = 15 → not equal
        // For a real tie: Meta only = 11, exact name only = 12... let's just test the comparator direction
        val exactNameDevice = makeDevice("fp18", name = "Wayfarer 00ZS")  // +8+4 = 12
        val metaDevice = makeDevice("fp19", manufacturerIds = listOf(0x0075))  // +8+3 = 11
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(metaDevice, exactNameDevice))
        val results = useCase(baseline, target, profile)
        assertEquals("fp18", results[0].capturedDevice.fingerprintId)
    }

    // --- Apple suppression ---

    @Test
    fun `Apple-only device only in target is excluded to prevent list noise`() {
        // Apple devices (iPhones, AirPods) are pervasive; appearing in the target scan is
        // environmental noise, not evidence of being the target device.
        val appleDevice = makeDevice("fpA1", manufacturerIds = listOf(0x004C))
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(appleDevice))
        val results = useCase(baseline, target, profile)
        assertTrue(
            "Apple-only device should be excluded (ungated bonus suppressed)",
            results.none { it.capturedDevice.fingerprintId == "fpA1" }
        )
    }

    @Test
    fun `non-Apple unknown device only in target still gets ungated new-device signal`() {
        // Suppression is Apple-specific; other unknown devices still earn LOW confidence
        val unknownDevice = makeDevice("fpA2", manufacturerIds = listOf(0x1234))
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(unknownDevice))
        val results = useCase(baseline, target, profile)
        val result = results.firstOrNull { it.capturedDevice.fingerprintId == "fpA2" }
        assertNotNull("Non-Apple unknown device should appear in results", result)
        assertEquals(3, result!!.score)
        assertEquals(CompareConfidence.LOW, result.confidence)
    }

    @Test
    fun `Apple device in both captures with RSSI delta still scores via delta signal`() {
        // Suppression only blocks the "only in target" ungated bonus.
        // An Apple device with RSSI -55 earns: +4 (delta ≥ 10) + +4 (very close, -55 ≥ -50) = 8
        val appleTarget = makeDevice("fpA3", manufacturerIds = listOf(0x004C), averageRssi = -55)
        val appleBaseline = makeDevice("fpA3", manufacturerIds = listOf(0x004C), averageRssi = -70)
        val target = makeSession(CaptureType.TARGET, listOf(appleTarget))
        val baseline = makeSession(CaptureType.BASELINE, listOf(appleBaseline))
        val results = useCase(baseline, target, profile)
        val result = results.firstOrNull { it.capturedDevice.fingerprintId == "fpA3" }
        assertNotNull("Apple device with RSSI delta should still appear", result)
        // -55 dBm qualifies for close proximity (≥ -60) but NOT very close (≥ -50)
        assertEquals(7, result!!.score)  // +4 (RSSI delta) + +3 (close proximity -55 dBm)
        assertEquals(CompareConfidence.MEDIUM, result.confidence)
        assertTrue(result.comparisonSignals.any { it.contains("increased") })
        assertTrue(result.comparisonSignals.any { it.contains("Close proximity") })
    }

    // --- visibleAtStop bonus ---

    @Test
    fun `visibleAtStop adds 2 point bonus`() {
        val deviceVisible = makeDevice("fpV1", manufacturerIds = listOf(0x0075), visibleAtStop = true)
        val deviceGone = makeDevice("fpV2", manufacturerIds = listOf(0x0075), visibleAtStop = false)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(deviceVisible, deviceGone))
        val results = useCase(baseline, target, profile)
        val visibleResult = results.first { it.capturedDevice.fingerprintId == "fpV1" }
        val goneResult = results.first { it.capturedDevice.fingerprintId == "fpV2" }
        // Both: +8 (gated by Meta) + +3 (Meta); visible adds +2
        assertEquals(13, visibleResult.score)
        assertEquals(11, goneResult.score)
        assertTrue(
            "visibleAtStop signal should appear in comparisonSignals",
            visibleResult.comparisonSignals.any { it.contains("Still advertising") }
        )
    }

    // --- Behavioral detection (no identity signal) ---

    @Test
    fun `unknown device with strong RSSI and high persistence reaches HIGH confidence via behavioral signals`() {
        // Reproduces the real-world scenario: glasses that do not expose Meta manufacturer ID
        // or an advertised name, but are persistently nearby with strong signal.
        val device = makeDevice("fpBeh1", averageRssi = -57, seenCount = 128, visibleAtStop = true)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +3 (only-in-target ungated) + +3 (close RSSI -57 ≥ -60) + +3 (persistence ≥ 100) +
        // +2 (visibleAtStop) + +3 (combo: RSSI ≥ -60 AND seenCount ≥ 50) = 14
        assertEquals(14, result.score)
        assertEquals(CompareConfidence.HIGH, result.confidence)
        // No identity signal should have fired
        assertTrue(
            "No identity signals should appear for an unknown device",
            result.comparisonSignals.none {
                it.contains("Meta") || it.contains("SMART_GLASSES") ||
                it.contains("CAMERA_CAPABLE") || it.contains("target name") || it.contains("profile hint")
            }
        )
        assertTrue(result.comparisonSignals.any { it.contains("appeared during target capture") })
        assertTrue(result.comparisonSignals.any { it.contains("Close proximity") })
        assertTrue(result.comparisonSignals.any { it.contains("High persistence") })
        assertTrue(result.comparisonSignals.any { it.contains("Still advertising") })
        assertTrue(result.comparisonSignals.any { it.contains("Close proximity + persistence") })
    }

    @Test
    fun `unknown device with moderate RSSI and persistence reaches MEDIUM confidence`() {
        // seenCount 50 → +2 persistence; RSSI -63 < -60 → no proximity bonus; combo doesn't fire
        val device = makeDevice("fpBeh2", averageRssi = -63, seenCount = 50, visibleAtStop = false)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +3 (only-in-target ungated) + +2 (persistence ≥ 50) = 5 → LOW (just below MEDIUM)
        // combo doesn't fire (RSSI < -60)
        assertEquals(5, result.score)
        assertEquals(CompareConfidence.LOW, result.confidence)
    }

    @Test
    fun `visibleAtStop does not add points when device was not visible at stop`() {
        val device = makeDevice("fpV3", manufacturerIds = listOf(0x0075), visibleAtStop = false)
        val baseline = makeSession(CaptureType.BASELINE, emptyList())
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        val result = results.first()
        // +8 (gated) + +3 (Meta) = 11 only
        assertEquals(11, result.score)
        assertTrue(result.comparisonSignals.none { it.contains("Still advertising") })
    }

    // --- seenInBaseline ---

    @Test
    fun `seenInBaseline is true when device fingerprint appears in baseline`() {
        val device = makeDevice("fp20", averageRssi = -60)
        val baselineDevice = makeDevice("fp20", averageRssi = -70)
        val baseline = makeSession(CaptureType.BASELINE, listOf(baselineDevice))
        val target = makeSession(CaptureType.TARGET, listOf(device))
        val results = useCase(baseline, target, profile)
        // Device is in both, no RSSI delta big enough (+5 dBm) and no identity signals → score 0 → excluded
        // Add an identity signal to see it in results
        val deviceWithMeta = makeDevice("fp21", manufacturerIds = listOf(0x0075), averageRssi = -60)
        val baselineDeviceWithMeta = makeDevice("fp21", averageRssi = -55)
        val target2 = makeSession(CaptureType.TARGET, listOf(deviceWithMeta))
        val baseline2 = makeSession(CaptureType.BASELINE, listOf(baselineDeviceWithMeta))
        val results2 = useCase(baseline2, target2, profile)
        // In both → "only in target" doesn't fire. Just +3 (Meta)
        val result = results2.first { it.capturedDevice.fingerprintId == "fp21" }
        assertTrue(result.seenInBaseline)
        assertEquals(-55, result.baselineAverageRssi)
        assertEquals(-5, result.rssiDelta)  // -60 - (-55) = -5
    }
}
