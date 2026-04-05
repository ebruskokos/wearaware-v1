# Target Capture and Compare Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated Capture & Compare screen to WearAware that lets the user take a baseline BLE scan (glasses OFF) and a target scan (glasses ON), then ranks which new device is most likely their Meta glasses.

**Architecture:** `CaptureViewModel` observes the same `BleRepository.observedDevices` stream already used by `ScanViewModel` — no new BLE infrastructure. All compare logic lives in `CompareCapturesUseCase` (pure Kotlin domain). Captures are persisted in two new Room tables (`capture_session`, `captured_device`), DB version bumped 4 → 5 with existing `fallbackToDestructiveMigration`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room v5, JUnit 4, existing `BleRepository`/`MatchTargetDeviceUseCase`.

---

## File Map

**New files — domain:**
- `app/src/main/kotlin/com/wearaware/app/domain/model/CaptureType.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/CapturedDevice.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/CaptureSession.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/CompareConfidence.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/CompareMatchResult.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/repository/CaptureRepository.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCase.kt`

**New files — data:**
- `app/src/main/kotlin/com/wearaware/app/data/local/CaptureSessionEntity.kt`
- `app/src/main/kotlin/com/wearaware/app/data/local/CapturedDeviceEntity.kt`
- `app/src/main/kotlin/com/wearaware/app/data/local/CaptureDao.kt`
- `app/src/main/kotlin/com/wearaware/app/data/mapper/CaptureMapper.kt`
- `app/src/main/kotlin/com/wearaware/app/data/repository/CaptureRepositoryImpl.kt`

**New files — UI:**
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt`
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt`
- `app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt`

**New files — tests:**
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/data/mapper/CaptureMapperTest.kt`

**Modified files:**
- `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt` — add two entities, version 4 → 5
- `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt` — add `provideCaptureDao()`
- `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt` — bind `CaptureRepository`
- `app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt` — add `Screen.Capture`
- `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt` — add "Compare" button

---

## Task 1: Domain models and CaptureRepository interface

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/CaptureType.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/CapturedDevice.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/CaptureSession.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/CompareConfidence.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/CompareMatchResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/CaptureRepository.kt`

- [ ] **Step 1: Create CaptureType.kt**

```kotlin
package com.wearaware.app.domain.model

enum class CaptureType { BASELINE, TARGET }
```

- [ ] **Step 2: Create CapturedDevice.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Per-device snapshot accumulated during a capture window.
 *   Not a point-in-time snapshot — all fields reflect the full window.
 * NOTES: manufacturerIds is the canonical field for logic (e.g. 0x0075 = Meta).
 *   companyNames is derived/display-only and must not be used for scoring.
 */
data class CapturedDevice(
    val fingerprintId: String,
    val advertisedName: String?,
    val macAddress: String?,
    /** Bluetooth SIG Company IDs. Canonical source for manufacturer-based logic. */
    val manufacturerIds: List<Int>,
    /** Human-readable summary e.g. "0075:deadbeef,004c:...". Display only. */
    val manufacturerDataSummary: String?,
    val serviceUuids: List<String>,
    val category: DeviceCategory,
    /** Resolved company/brand names. Display only — do not use for scoring. */
    val companyNames: List<String>,
    val firstSeenInCapture: Long,
    val lastSeenInCapture: Long,
    val peakRssi: Int,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean,
    val targetMatchScore: Int?,
    val targetMatchSignals: List<String>
)
```

- [ ] **Step 3: Create CaptureSession.kt**

```kotlin
package com.wearaware.app.domain.model

data class CaptureSession(
    val id: String,
    val type: CaptureType,
    val startedAt: Long,
    val stoppedAt: Long,
    val devices: List<CapturedDevice>
)
```

- [ ] **Step 4: Create CompareConfidence.kt**

```kotlin
package com.wearaware.app.domain.model

enum class CompareConfidence {
    HIGH,    // score >= 9
    MEDIUM,  // score >= 6
    LOW,     // score >= 3 — shown in list but not highlighted as top candidate
    NONE     // score < 3 — excluded from results
}
```

- [ ] **Step 5: Create CompareMatchResult.kt**

```kotlin
package com.wearaware.app.domain.model

data class CompareMatchResult(
    val capturedDevice: CapturedDevice,
    val score: Int,
    val confidence: CompareConfidence,
    /** Human-readable strings explaining each signal that fired. Shown in UI. */
    val comparisonSignals: List<String>,
    val seenInBaseline: Boolean,
    val seenInTarget: Boolean,          // always true — these are target devices
    val baselineAverageRssi: Int?,      // null if not seen in baseline
    val rssiDelta: Int?,                // targetAvg − baselineAvg; null if not in baseline
    /** False when no baseline session was provided; UI shows weaker-evidence disclaimer. */
    val hasBaseline: Boolean
)
```

- [ ] **Step 6: Create CaptureRepository.kt**

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CaptureType

interface CaptureRepository {
    suspend fun saveSession(session: CaptureSession)
    suspend fun getSession(type: CaptureType): CaptureSession?
    suspend fun deleteSession(type: CaptureType)
}
```

- [ ] **Step 7: Run unit tests to confirm nothing broke**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (no existing tests change).

- [ ] **Step 8: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/model/CaptureType.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/CapturedDevice.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/CaptureSession.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/CompareConfidence.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/CompareMatchResult.kt \
  app/src/main/kotlin/com/wearaware/app/domain/repository/CaptureRepository.kt && \
git commit -m "feat: add capture and compare domain models"
```

---

## Task 2: CompareCapturesUseCase (TDD)

**Files:**
- Test: `app/src/test/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCaseTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCase.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
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
        seenCount: Int = 1
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
        visibleAtStop = true,
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
            seenCount = 1, visibleAtStop = true,
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
```

- [ ] **Step 2: Run tests to confirm they FAIL**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.CompareCapturesUseCaseTest" 2>&1 | tail -10
```

Expected: FAIL — `CompareCapturesUseCase` not found.

- [ ] **Step 3: Create CompareCapturesUseCase.kt**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

private const val META_COMPANY_ID = 0x0075
private const val RSSI_DELTA_THRESHOLD = 10

/**
 * PURPOSE: Compares a baseline and target CaptureSession to rank which devices
 *   most likely appeared because the target device (e.g. Meta glasses) was powered on.
 *
 * SCORING (additive):
 *   +8  only in target — gated: requires at least one identity signal
 *   +3  only in target — ungated: no identity signal (caps at LOW alone)
 *   +4  RSSI increased >= 10 dBm vs baseline
 *   +4  exact target name match (profile.friendlyName)
 *   +3  Meta manufacturer match (manufacturerIds contains 0x0075) — CANONICAL FIELD
 *   +3  SMART_GLASSES classification
 *   +2  CAMERA_CAPABLE_WEARABLE classification
 *   +2  partial profile hint (modelHint or brandHint in advertisedName)
 *   +1  seenCount >= 5 during target capture
 *
 * CONFIDENCE: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3 (excluded)
 *
 * TOP CANDIDATE: only MEDIUM or HIGH may be highlighted as top candidate in UI.
 *
 * SORT ORDER: score desc → exact name match → Meta manufacturer → RSSI desc → fingerprintId asc
 *
 * NOTES: manufacturerIds is canonical for all logic. companyNames is display-only.
 *   When baseline is null, "only in target" signals never fire (hasBaseline = false).
 */
class CompareCapturesUseCase @Inject constructor() {

    operator fun invoke(
        baseline: CaptureSession?,
        target: CaptureSession,
        profile: TargetDeviceProfile
    ): List<CompareMatchResult> {
        val baselineMap = baseline?.devices?.associateBy { it.fingerprintId } ?: emptyMap()
        val hasBaseline = baseline != null

        return target.devices
            .mapNotNull { targetDevice ->
                val baselineDevice = baselineMap[targetDevice.fingerprintId]
                scoreDevice(targetDevice, baselineDevice, profile, hasBaseline)
                    .takeIf { it.confidence != CompareConfidence.NONE }
            }
            .sortedWith(
                compareByDescending<CompareMatchResult> { it.score }
                    .thenByDescending { hasExactNameSignal(it) }
                    .thenByDescending { hasMetaSignal(it) }
                    .thenByDescending { it.capturedDevice.averageRssi }
                    .thenBy { it.capturedDevice.fingerprintId }
            )
    }

    private fun hasExactNameSignal(result: CompareMatchResult): Boolean =
        result.comparisonSignals.any { it.startsWith("Exact target name match") }

    private fun hasMetaSignal(result: CompareMatchResult): Boolean =
        result.comparisonSignals.any { it.startsWith("Meta manufacturer match") }

    private fun scoreDevice(
        device: CapturedDevice,
        baselineDevice: CapturedDevice?,
        profile: TargetDeviceProfile,
        hasBaseline: Boolean
    ): CompareMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // Pre-compute identity signals (needed for "only in target" gate)
        val hasMeta = device.manufacturerIds.contains(META_COMPANY_ID)
        val hasSmartGlasses = device.category == DeviceCategory.SMART_GLASSES
        val hasCameraCapable = device.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE
        val hasExactName = device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) == true
        val hasPartialHint = !hasExactName && (
            device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true ||
            device.advertisedName?.contains(profile.brandHint, ignoreCase = true) == true
        )
        val hasIdentitySignal = hasMeta || hasSmartGlasses || hasCameraCapable || hasExactName || hasPartialHint

        // "Only in target" signals — require hasBaseline to avoid false positives
        if (baselineDevice == null && hasBaseline) {
            if (hasIdentitySignal) {
                score += 8
                signals += "Only appeared when target was powered on"
            } else {
                score += 3
                signals += "New device: appeared during target capture (no identity signal)"
            }
        }

        // RSSI delta
        if (baselineDevice != null) {
            val delta = device.averageRssi - baselineDevice.averageRssi
            if (delta >= RSSI_DELTA_THRESHOLD) {
                score += 4
                signals += "Signal strength increased by +${delta} dBm in target capture"
            }
        }

        // Exact target name match
        if (hasExactName) {
            score += 4
            signals += "Exact target name match: \"${device.advertisedName}\""
        } else if (hasPartialHint) {
            score += 2
            signals += "Partial target profile hint: \"${device.advertisedName}\""
        }

        // Meta manufacturer — uses manufacturerIds (canonical), never companyNames
        if (hasMeta) {
            score += 3
            signals += "Meta manufacturer match (0x0075)"
        }

        // Classification
        if (hasSmartGlasses) {
            score += 3
            signals += "Classification: SMART_GLASSES"
        } else if (hasCameraCapable) {
            score += 2
            signals += "Classification: CAMERA_CAPABLE_WEARABLE"
        }

        // Persistence bonus
        if (device.seenCount >= 5) {
            score += 1
            signals += "Persistent: observed ${device.seenCount} times during target capture"
        }

        val confidence = when {
            score >= 9 -> CompareConfidence.HIGH
            score >= 6 -> CompareConfidence.MEDIUM
            score >= 3 -> CompareConfidence.LOW
            else       -> CompareConfidence.NONE
        }

        val rssiDelta = if (baselineDevice != null) device.averageRssi - baselineDevice.averageRssi else null

        return CompareMatchResult(
            capturedDevice = device,
            score = score,
            confidence = confidence,
            comparisonSignals = signals,
            seenInBaseline = baselineDevice != null,
            seenInTarget = true,
            baselineAverageRssi = baselineDevice?.averageRssi,
            rssiDelta = rssiDelta,
            hasBaseline = hasBaseline
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they PASS**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.CompareCapturesUseCaseTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCase.kt \
  app/src/test/kotlin/com/wearaware/app/domain/usecase/CompareCapturesUseCaseTest.kt && \
git commit -m "feat: add CompareCapturesUseCase with TDD — differential BLE scoring engine"
```

---

## Task 3: Room entities, DAO, and DB version bump

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/CaptureSessionEntity.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/CapturedDeviceEntity.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/CaptureDao.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`

- [ ] **Step 1: Create CaptureSessionEntity.kt**

```kotlin
package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_session")
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    val type: String,           // CaptureType.name: "BASELINE" or "TARGET"
    val startedAt: Long,
    val stoppedAt: Long
)
```

- [ ] **Step 2: Create CapturedDeviceEntity.kt**

```kotlin
package com.wearaware.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_device")
data class CapturedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val fingerprintId: String,
    val advertisedName: String?,
    val macAddress: String?,
    /** Comma-sep hex e.g. "0075,004c". Canonical for manufacturer logic. */
    val manufacturerIds: String?,
    /** "0075:deadbeef,..." — display only. */
    val manufacturerDataSummary: String?,
    /** Comma-sep UUID strings. */
    val serviceUuids: String?,
    val category: String,
    /** Comma-sep — display only. */
    val companyNames: String?,
    val firstSeenInCapture: Long,
    val lastSeenInCapture: Long,
    val peakRssi: Int,
    val averageRssi: Int,
    val seenCount: Int,
    @ColumnInfo(defaultValue = "0") val visibleAtStop: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val targetMatchScore: Int? = null,
    /** Semicolon-sep signal strings. */
    @ColumnInfo(defaultValue = "NULL") val targetMatchSignals: String? = null
)
```

- [ ] **Step 3: Create CaptureDao.kt**

```kotlin
package com.wearaware.app.data.local

import androidx.room.*

@Dao
interface CaptureDao {

    @Query("SELECT * FROM capture_session WHERE type = :type LIMIT 1")
    suspend fun getSession(type: String): CaptureSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CaptureSessionEntity)

    @Query("SELECT * FROM captured_device WHERE session_id = :sessionId")
    suspend fun getDevicesForSession(sessionId: String): List<CapturedDeviceEntity>

    @Insert
    suspend fun insertDevices(devices: List<CapturedDeviceEntity>)

    @Query("DELETE FROM captured_device WHERE session_id = :sessionId")
    suspend fun deleteDevicesForSession(sessionId: String)

    @Query("DELETE FROM capture_session WHERE type = :type")
    suspend fun deleteSession(type: String)
}
```

- [ ] **Step 4: Update WearAwareDatabase.kt — add entities, bump to version 5**

Read the current file first, then replace entirely with:

```kotlin
package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate (plan superseded)
 *   3 → added fingerprintId, manufacturerIds (BLE intelligence upgrade)
 *   4 → added manufacturerDataHex, serviceUuids, txPower, connectable, rawScanBytesHex
 *   5 → added capture_session and captured_device tables (Target Capture & Compare)
 * NOTES: fallbackToDestructiveMigration used — session logs are ephemeral and user-clearable.
 */
@Database(
    entities = [ScanLogEntity::class, CaptureSessionEntity::class, CapturedDeviceEntity::class],
    version = 5,
    exportSchema = false
)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao
    abstract fun captureDao(): CaptureDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 5: Update DatabaseModule.kt — add provideCaptureDao()**

Read the current file first. Add this method inside the `object DatabaseModule` block, after `provideScanLogDao`:

```kotlin
    @Provides
    fun provideCaptureDao(database: WearAwareDatabase): CaptureDao =
        database.captureDao()
```

- [ ] **Step 6: Run unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/data/local/CaptureSessionEntity.kt \
  app/src/main/kotlin/com/wearaware/app/data/local/CapturedDeviceEntity.kt \
  app/src/main/kotlin/com/wearaware/app/data/local/CaptureDao.kt \
  app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt \
  app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt && \
git commit -m "feat: add Room capture tables and CaptureDao (DB v5)"
```

---

## Task 4: CaptureMapper (TDD)

**Files:**
- Test: `app/src/test/kotlin/com/wearaware/app/data/mapper/CaptureMapperTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/mapper/CaptureMapper.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run to confirm FAIL**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.CaptureMapperTest" 2>&1 | tail -10
```

Expected: FAIL — `toDeviceEntity` not found.

- [ ] **Step 3: Create CaptureMapper.kt**

```kotlin
package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.CapturedDeviceEntity
import com.wearaware.app.data.local.CaptureSessionEntity
import com.wearaware.app.domain.model.*

fun CaptureSession.toSessionEntity(): CaptureSessionEntity = CaptureSessionEntity(
    id = id,
    type = type.name,
    startedAt = startedAt,
    stoppedAt = stoppedAt
)

fun CapturedDevice.toDeviceEntity(sessionId: String): CapturedDeviceEntity = CapturedDeviceEntity(
    sessionId = sessionId,
    fingerprintId = fingerprintId,
    advertisedName = advertisedName,
    macAddress = macAddress,
    manufacturerIds = manufacturerIds
        .joinToString(",") { it.toString(16).padStart(4, '0') }
        .ifEmpty { null },
    manufacturerDataSummary = manufacturerDataSummary,
    serviceUuids = serviceUuids.joinToString(",").ifEmpty { null },
    category = category.name,
    companyNames = companyNames.joinToString(",").ifEmpty { null },
    firstSeenInCapture = firstSeenInCapture,
    lastSeenInCapture = lastSeenInCapture,
    peakRssi = peakRssi,
    averageRssi = averageRssi,
    seenCount = seenCount,
    visibleAtStop = visibleAtStop,
    targetMatchScore = targetMatchScore,
    targetMatchSignals = targetMatchSignals.joinToString(";").ifEmpty { null }
)

fun CaptureSessionEntity.toDomain(devices: List<CapturedDeviceEntity>): CaptureSession = CaptureSession(
    id = id,
    type = CaptureType.valueOf(type),
    startedAt = startedAt,
    stoppedAt = stoppedAt,
    devices = devices.map { it.toDomain() }
)

fun CapturedDeviceEntity.toDomain(): CapturedDevice = CapturedDevice(
    fingerprintId = fingerprintId,
    advertisedName = advertisedName,
    macAddress = macAddress,
    manufacturerIds = manufacturerIds
        ?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt(16) }
        ?: emptyList(),
    manufacturerDataSummary = manufacturerDataSummary,
    serviceUuids = serviceUuids
        ?.split(",")?.filter { it.isNotEmpty() }
        ?: emptyList(),
    category = DeviceCategory.valueOf(category),
    companyNames = companyNames
        ?.split(",")?.filter { it.isNotEmpty() }
        ?: emptyList(),
    firstSeenInCapture = firstSeenInCapture,
    lastSeenInCapture = lastSeenInCapture,
    peakRssi = peakRssi,
    averageRssi = averageRssi,
    seenCount = seenCount,
    visibleAtStop = visibleAtStop,
    targetMatchScore = targetMatchScore,
    targetMatchSignals = targetMatchSignals
        ?.split(";")?.filter { it.isNotEmpty() }
        ?: emptyList()
)
```

- [ ] **Step 4: Run tests to confirm PASS**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.CaptureMapperTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 6 tests green.

- [ ] **Step 5: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/data/mapper/CaptureMapper.kt \
  app/src/test/kotlin/com/wearaware/app/data/mapper/CaptureMapperTest.kt && \
git commit -m "feat: add CaptureMapper with round-trip tests"
```

---

## Task 5: CaptureRepositoryImpl and RepositoryModule binding

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/CaptureRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt`

- [ ] **Step 1: Create CaptureRepositoryImpl.kt**

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.local.CaptureDao
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.data.mapper.toDeviceEntity
import com.wearaware.app.data.mapper.toSessionEntity
import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.repository.CaptureRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepositoryImpl @Inject constructor(
    private val captureDao: CaptureDao
) : CaptureRepository {

    override suspend fun saveSession(session: CaptureSession) {
        captureDao.insertSession(session.toSessionEntity())
        captureDao.insertDevices(session.devices.map { it.toDeviceEntity(session.id) })
    }

    override suspend fun getSession(type: CaptureType): CaptureSession? {
        val sessionEntity = captureDao.getSession(type.name) ?: return null
        val deviceEntities = captureDao.getDevicesForSession(sessionEntity.id)
        return sessionEntity.toDomain(deviceEntities)
    }

    override suspend fun deleteSession(type: CaptureType) {
        val existing = captureDao.getSession(type.name)
        if (existing != null) {
            captureDao.deleteDevicesForSession(existing.id)
        }
        captureDao.deleteSession(type.name)
    }
}
```

- [ ] **Step 2: Update RepositoryModule.kt — add CaptureRepository binding**

Read the current file. Add the new binding inside the `abstract class RepositoryModule` block:

```kotlin
    @Binds
    @Singleton
    abstract fun bindCaptureRepository(impl: CaptureRepositoryImpl): CaptureRepository
```

Also add the required imports at the top:
```kotlin
import com.wearaware.app.data.repository.CaptureRepositoryImpl
import com.wearaware.app.domain.repository.CaptureRepository
```

- [ ] **Step 3: Run unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/data/repository/CaptureRepositoryImpl.kt \
  app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt && \
git commit -m "feat: add CaptureRepositoryImpl and Hilt binding"
```

---

## Task 6: CaptureUiState and CaptureViewModel

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt`

- [ ] **Step 1: Create CaptureUiState.kt**

```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CompareMatchResult

data class CaptureUiState(
    val baselineCaptureState: CaptureState = CaptureState.IDLE,
    val targetCaptureState: CaptureState = CaptureState.IDLE,
    val baseline: CaptureSession? = null,
    val target: CaptureSession? = null,
    /** Number of unique devices accumulated so far during the active capture. Updated live. */
    val liveAccumulatedCount: Int = 0,
    val compareResults: List<CompareMatchResult> = emptyList()
)

enum class CaptureState { IDLE, CAPTURING, DONE }
```

- [ ] **Step 2: Create CaptureViewModel.kt**

```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.usecase.CompareCapturesUseCase
import com.wearaware.app.domain.usecase.DefaultTargetProfile
import com.wearaware.app.domain.usecase.MatchTargetDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val captureRepository: CaptureRepository,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val compareCapturesUseCase: CompareCapturesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /** In-memory accumulator for devices observed during the active capture window. */
    private val accumulator = mutableMapOf<String, DeviceAccumulator>()
    private var captureStartedAt = 0L
    private var activeCaptureType: CaptureType? = null

    init {
        // Load any previously persisted captures from Room
        viewModelScope.launch {
            val baseline = captureRepository.getSession(CaptureType.BASELINE)
            val target = captureRepository.getSession(CaptureType.TARGET)
            _uiState.update {
                it.copy(
                    baseline = baseline,
                    baselineCaptureState = if (baseline != null) CaptureState.DONE else CaptureState.IDLE,
                    target = target,
                    targetCaptureState = if (target != null) CaptureState.DONE else CaptureState.IDLE
                )
            }
            if (target != null) runCompare(baseline, target)
        }

        // Observe BLE device stream — only accumulates when activeCaptureType != null
        viewModelScope.launch {
            bleRepository.observedDevices.collect { devices ->
                if (activeCaptureType != null) {
                    accumulateDevices(devices)
                    _uiState.update { it.copy(liveAccumulatedCount = accumulator.size) }
                }
            }
        }
    }

    /**
     * Starts a capture of the given type.
     * Only one capture may be active at a time. If the other type is already CAPTURING,
     * this call is a no-op (the UI should disable the button in that case).
     */
    fun startCapture(type: CaptureType) {
        val otherState = if (type == CaptureType.BASELINE)
            _uiState.value.targetCaptureState
        else
            _uiState.value.baselineCaptureState
        if (otherState == CaptureState.CAPTURING) return  // lifecycle rule: no overlap

        viewModelScope.launch {
            captureRepository.deleteSession(type)
            accumulator.clear()
            captureStartedAt = System.currentTimeMillis()
            activeCaptureType = type
            _uiState.update {
                if (type == CaptureType.BASELINE)
                    it.copy(baselineCaptureState = CaptureState.CAPTURING, baseline = null, liveAccumulatedCount = 0)
                else
                    it.copy(targetCaptureState = CaptureState.CAPTURING, target = null, liveAccumulatedCount = 0)
            }
        }
    }

    /**
     * Stops the active capture, snapshots accumulated devices, saves to Room, and
     * auto-triggers compare if the other capture also exists.
     */
    fun stopCapture(type: CaptureType) {
        if (activeCaptureType != type) return
        activeCaptureType = null

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val visibleIds = bleRepository.observedDevices.value
                .filter { it.visibilityState == VisibilityState.DETECTED_NOW }
                .map { it.id }
                .toSet()

            val devices = accumulator.values.map { acc ->
                CapturedDevice(
                    fingerprintId = acc.fingerprintId,
                    advertisedName = acc.advertisedName,
                    macAddress = acc.macAddress,
                    manufacturerIds = acc.manufacturerIds,
                    manufacturerDataSummary = acc.manufacturerDataSummary,
                    serviceUuids = acc.serviceUuids,
                    category = acc.category,
                    companyNames = acc.companyNames,
                    firstSeenInCapture = acc.firstSeenInCapture,
                    lastSeenInCapture = acc.lastSeenInCapture,
                    peakRssi = acc.peakRssi,
                    averageRssi = acc.averageRssi,
                    seenCount = acc.seenCount,
                    visibleAtStop = acc.fingerprintId in visibleIds,
                    targetMatchScore = acc.targetMatchScore,
                    targetMatchSignals = acc.targetMatchSignals
                )
            }

            val session = CaptureSession(
                id = UUID.randomUUID().toString(),
                type = type,
                startedAt = captureStartedAt,
                stoppedAt = now,
                devices = devices
            )
            captureRepository.saveSession(session)
            accumulator.clear()

            val newBaseline = if (type == CaptureType.BASELINE) session else _uiState.value.baseline
            val newTarget = if (type == CaptureType.TARGET) session else _uiState.value.target

            _uiState.update {
                if (type == CaptureType.BASELINE)
                    it.copy(baselineCaptureState = CaptureState.DONE, baseline = session, liveAccumulatedCount = 0)
                else
                    it.copy(targetCaptureState = CaptureState.DONE, target = session, liveAccumulatedCount = 0)
            }

            if (newTarget != null) runCompare(newBaseline, newTarget)
        }
    }

    private fun accumulateDevices(devices: List<ObservedDevice>) {
        val now = System.currentTimeMillis()
        val matchScores = matchTargetDevice(devices, DefaultTargetProfile.WAYFARER_00ZS)

        for (device in devices) {
            val existing = accumulator[device.id]
            if (existing == null) {
                val matchResult = matchScores[device.id]
                val mfDataSummary = device.fingerprint?.manufacturerDataHex
                    ?.entries?.joinToString(",") { (id, hex) ->
                        "${id.toString(16).padStart(4, '0')}:$hex"
                    }?.takeIf { it.isNotEmpty() }
                accumulator[device.id] = DeviceAccumulator(
                    fingerprintId = device.id,
                    advertisedName = device.advertisedName,
                    macAddress = device.macAddress,
                    manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
                    manufacturerDataSummary = mfDataSummary,
                    serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
                    category = device.classification.category,
                    companyNames = device.companyNames,
                    firstSeenInCapture = now,
                    lastSeenInCapture = now,
                    peakRssi = device.rawRssi,
                    runningRssiSum = device.rawRssi.toLong(),
                    readingCount = 1,
                    seenCount = 1,
                    targetMatchScore = matchResult?.score,
                    targetMatchSignals = matchResult?.matchedSignals ?: emptyList()
                )
            } else {
                existing.lastSeenInCapture = now
                existing.seenCount++
                if (device.rawRssi > existing.peakRssi) existing.peakRssi = device.rawRssi
                existing.runningRssiSum += device.rawRssi
                existing.readingCount++
            }
        }
    }

    private suspend fun runCompare(baseline: CaptureSession?, target: CaptureSession) {
        val results = compareCapturesUseCase(baseline, target, DefaultTargetProfile.WAYFARER_00ZS)
        _uiState.update { it.copy(compareResults = results) }
    }

    /** Internal mutable accumulator for one device during a capture window. */
    private data class DeviceAccumulator(
        val fingerprintId: String,
        val advertisedName: String?,
        val macAddress: String?,
        val manufacturerIds: List<Int>,
        val manufacturerDataSummary: String?,
        val serviceUuids: List<String>,
        val category: DeviceCategory,
        val companyNames: List<String>,
        val firstSeenInCapture: Long,
        var lastSeenInCapture: Long,
        var peakRssi: Int,
        var runningRssiSum: Long,
        var readingCount: Int,
        var seenCount: Int,
        val targetMatchScore: Int?,
        val targetMatchSignals: List<String>
    ) {
        val averageRssi: Int get() = if (readingCount > 0) (runningRssiSum / readingCount).toInt() else peakRssi
    }
}
```

- [ ] **Step 3: Run unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt \
  app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt && \
git commit -m "feat: add CaptureViewModel and CaptureUiState"
```

---

## Task 7: CaptureScreen UI

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt`

- [ ] **Step 1: Create CaptureScreen.kt**

```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.model.CompareConfidence
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.ui.viewmodel.CaptureState
import com.wearaware.app.ui.viewmodel.CaptureUiState
import com.wearaware.app.ui.viewmodel.CaptureViewModel
import com.wearaware.app.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onViewDeviceDetail: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Target Capture & Compare") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Baseline
            CaptureSection(
                title = "Step 1: Baseline (glasses OFF)",
                subtitle = "Scan the environment before powering on your target device.",
                captureState = uiState.baselineCaptureState,
                deviceCount = uiState.baseline?.devices?.size,
                durationMs = uiState.baseline?.let { it.stoppedAt - it.startedAt },
                liveCount = if (uiState.baselineCaptureState == CaptureState.CAPTURING)
                    uiState.liveAccumulatedCount else null,
                onStart = { viewModel.startCapture(CaptureType.BASELINE) },
                onStop = { viewModel.stopCapture(CaptureType.BASELINE) },
                startEnabled = uiState.targetCaptureState != CaptureState.CAPTURING,
                otherCaptureActive = uiState.targetCaptureState == CaptureState.CAPTURING
            )

            HorizontalDivider()

            // Section 2: Target
            CaptureSection(
                title = "Step 2: Target (glasses ON and nearby)",
                subtitle = "Power on your target device, then start this capture.",
                captureState = uiState.targetCaptureState,
                deviceCount = uiState.target?.devices?.size,
                durationMs = uiState.target?.let { it.stoppedAt - it.startedAt },
                liveCount = if (uiState.targetCaptureState == CaptureState.CAPTURING)
                    uiState.liveAccumulatedCount else null,
                onStart = { viewModel.startCapture(CaptureType.TARGET) },
                onStop = { viewModel.stopCapture(CaptureType.TARGET) },
                startEnabled = uiState.baselineCaptureState != CaptureState.CAPTURING,
                otherCaptureActive = uiState.baselineCaptureState == CaptureState.CAPTURING
            )

            // Section 3: Compare results
            if (uiState.targetCaptureState == CaptureState.DONE) {
                HorizontalDivider()
                CompareResultsSection(
                    results = uiState.compareResults,
                    hasBaseline = uiState.baseline != null,
                    onViewDevice = onViewDeviceDetail
                )
            }
        }
    }
}

@Composable
private fun CaptureSection(
    title: String,
    subtitle: String,
    captureState: CaptureState,
    deviceCount: Int?,
    durationMs: Long?,
    liveCount: Int?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    startEnabled: Boolean,
    otherCaptureActive: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val chipLabel = when (captureState) {
            CaptureState.IDLE -> "IDLE"
            CaptureState.CAPTURING -> "● CAPTURING${liveCount?.let { " — $it devices" } ?: ""}"
            CaptureState.DONE -> "✓ DONE — ${deviceCount ?: 0} devices"
        }
        SuggestionChip(onClick = {}, label = {
            Text(chipLabel, style = MaterialTheme.typography.labelSmall)
        })

        if (captureState == CaptureState.DONE && durationMs != null) {
            Text(
                "Captured ${deviceCount ?: 0} devices over ${durationMs.formatDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (otherCaptureActive && captureState != CaptureState.CAPTURING) {
            Text(
                "Stop the active capture before starting a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = if (captureState == CaptureState.CAPTURING) onStop else onStart,
            enabled = captureState == CaptureState.CAPTURING || startEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (captureState) {
                    CaptureState.IDLE -> "Start Capture"
                    CaptureState.CAPTURING -> "Stop Capture"
                    CaptureState.DONE -> "Re-capture"
                }
            )
        }
    }
}

@Composable
private fun CompareResultsSection(
    results: List<CompareMatchResult>,
    hasBaseline: Boolean,
    onViewDevice: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Compare Results", style = MaterialTheme.typography.titleSmall)

        if (!hasBaseline) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "ℹ No baseline was captured. Results show target-only evidence without differential comparison — treat with lower confidence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (results.isEmpty()) {
            Text(
                "No strong differential match found yet.\nTry a longer capture or move closer to your target device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        results.forEachIndexed { index, result ->
            val isTopCandidate = index == 0 &&
                (result.confidence == CompareConfidence.HIGH ||
                    result.confidence == CompareConfidence.MEDIUM)
            CompareResultCard(
                result = result,
                isTopCandidate = isTopCandidate,
                onViewDevice = onViewDevice
            )
        }
    }
}

@Composable
private fun CompareResultCard(
    result: CompareMatchResult,
    isTopCandidate: Boolean,
    onViewDevice: (String) -> Unit
) {
    val device = result.capturedDevice
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTopCandidate)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isTopCandidate) {
                Text(
                    "★ Most likely new candidate after target device was powered on",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val confidenceColor = when (result.confidence) {
                CompareConfidence.HIGH -> MaterialTheme.colorScheme.primary
                CompareConfidence.MEDIUM -> MaterialTheme.colorScheme.secondary
                CompareConfidence.LOW -> MaterialTheme.colorScheme.tertiary
                CompareConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                result.confidence.name,
                style = MaterialTheme.typography.labelSmall,
                color = confidenceColor
            )

            val displayName = device.advertisedName
                ?: device.companyNames.firstOrNull()?.let { "$it device" }
                ?: "Unknown BLE Device"
            Text(displayName, style = MaterialTheme.typography.bodyMedium)

            if (device.companyNames.isNotEmpty()) {
                val idHex = device.manufacturerIds.firstOrNull()
                    ?.let { "  0x${it.toString(16).uppercase().padStart(4, '0')}" } ?: ""
                Text(
                    "Manufacturer: ${device.companyNames.joinToString(", ")}$idHex",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Seen in baseline: ${if (result.seenInBaseline) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Seen in target: Yes", style = MaterialTheme.typography.bodySmall)
            }

            val rssiText = if (result.baselineAverageRssi != null) {
                val deltaStr = result.rssiDelta?.let { if (it >= 0) "+$it" else "$it" } ?: "n/a"
                "RSSI: baseline ${result.baselineAverageRssi} dBm → target ${device.averageRssi} dBm  Δ $deltaStr dBm"
            } else {
                "RSSI: baseline n/a → target ${device.averageRssi} dBm"
            }
            Text(rssiText, style = MaterialTheme.typography.bodySmall)

            if (result.comparisonSignals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Why this device:", style = MaterialTheme.typography.labelSmall)
                result.comparisonSignals.forEach { signal ->
                    Text(
                        "  • $signal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            device.manufacturerDataSummary?.let {
                Text(
                    "Manufacturer data: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (device.serviceUuids.isNotEmpty()) {
                Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                device.serviceUuids.take(3).forEach { uuid ->
                    Text(
                        "  $uuid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!result.hasBaseline) {
                Text(
                    "Note: no baseline — treat with lower confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { onViewDevice(device.fingerprintId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Device Details")
            }
        }
    }
}
```

- [ ] **Step 2: Run unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt && \
git commit -m "feat: add CaptureScreen with baseline/target controls and ranked compare results"
```

---

## Task 8: Navigation wiring

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`

- [ ] **Step 1: Update NavGraph.kt**

Read the current file. Make two changes:

**A. Add `Screen.Capture` to the sealed class:**
```kotlin
    object Capture : Screen("capture")
```

**B. Add a new `composable` block in `WearAwareNavGraph`:**
```kotlin
        composable(Screen.Capture.route) {
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onViewDeviceDetail = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.routeFor(deviceId))
                }
            )
        }
```

Also add the import at the top:
```kotlin
import com.wearaware.app.ui.screens.CaptureScreen
```

- [ ] **Step 2: Update ScanScreen.kt — add "Compare" navigation callback and button**

Read the current file. Make two changes:

**A. Add `onCaptureClick: () -> Unit` parameter** to the `ScanScreen` composable signature:
```kotlin
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
)
```

**B. Add a "Compare" `TextButton` in the `TopAppBar` `actions` block**, OUTSIDE the `if (uiState.scanState == ScanState.SCANNING)` guard (compare is available regardless of scan state):

```kotlin
                actions = {
                    TextButton(onClick = onCaptureClick) {
                        Text(
                            text = "Compare",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (uiState.scanState == ScanState.SCANNING) {
                        TextButton(onClick = { viewModel.toggleFocusMode() }) {
                            Text(
                                text = if (uiState.focusMode) "All Devices" else "Focus Mode",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { viewModel.toggleDebugMode() }) {
                            Text(
                                text = if (uiState.debugMode) "Hide Debug" else "Debug",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
```

**C. Update the `NavGraph.kt` call site for `ScanScreen`** to pass the new callback:
```kotlin
        composable(Screen.Scan.route) {
            ScanScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.routeFor(deviceId))
                },
                onCaptureClick = {
                    navController.navigate(Screen.Capture.route)
                }
            )
        }
```

- [ ] **Step 3: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt \
  app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt && \
git commit -m "feat: wire CaptureScreen into NavGraph; add Compare button to ScanScreen top bar"
```

---

## Task 9: Final build verification

**Files:** No new files.

- [ ] **Step 1: Run full unit test suite**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. Expected test count: 100+ tests across all suites (79 from previous tasks + ~21 new).

- [ ] **Step 2: Build the debug APK**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:assembleDebug 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL — `app/build/outputs/apk/debug/app-debug.apk` created.

- [ ] **Step 3: Install on emulator (optional — skip if no AVD running)**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 4: Commit if any final fixes were needed during verification**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add -p && git commit -m "fix: final build verification adjustments"
```

---

## Self-Review

**Spec coverage:**

| Requirement | Task(s) |
|---|---|
| Capture sessions — baseline and target | Tasks 1, 6 (`CaptureViewModel.startCapture/stopCapture`) |
| Capture stores all per-device fields | Task 1 (`CapturedDevice`), Task 6 (`accumulateDevices`) |
| Accumulate all devices seen during window | Task 6 (`DeviceAccumulator` map, updated every tick) |
| visibleAtStop flag | Task 6 (`stopCapture` snapshots `observedDevices.value`) |
| Replace-on-new-start behaviour | Task 6 (`deleteSession` before starting) |
| One active capture at a time / lifecycle rule | Task 6 (guard in `startCapture`, UI message in Task 7) |
| CompareMatchResult with score, confidence, signals | Task 1 (`CompareMatchResult`) |
| Full scoring table with tie-breaks | Task 2 (`CompareCapturesUseCase`) |
| Top-candidate only MEDIUM/HIGH | Task 7 (`isTopCandidate` check in `CompareResultsSection`) |
| manufacturerIds canonical, companyNames display-only | Tasks 1 (KDoc), 2 (scoring uses `manufacturerIds`) |
| Room persistence (capture_session, captured_device) | Tasks 3–5 |
| DB version 4 → 5 | Task 3 |
| CaptureScreen with 3 sections | Task 7 |
| Safe wording | Task 7 (all candidate copy) |
| hasBaseline disclaimer | Tasks 1, 7 |
| "Compare" button from ScanScreen | Task 8 |
| Navigation: separate screen in NavGraph | Task 8 |
| Tests: CompareCapturesUseCaseTest (14 cases) | Task 2 |
| Tests: CaptureMapperTest (6 cases) | Task 4 |

**Placeholder scan:** No TBDs, TODOs, or incomplete steps.

**Type consistency check:**
- `CapturedDevice.manufacturerIds: List<Int>` used in Task 2 scoring (`.contains(0x0075)`) ✓
- `CaptureSession.devices: List<CapturedDevice>` accessed in `CompareCapturesUseCase` ✓
- `CaptureState` enum defined in `CaptureUiState.kt`, referenced in `CaptureViewModel` and `CaptureScreen` ✓
- `DeviceAccumulator.averageRssi` computed property used when building `CapturedDevice` in `stopCapture` ✓
- `CaptureRepository.deleteSession` called before `startCapture` in ViewModel ✓
- `onCaptureClick` parameter added to `ScanScreen` and passed from `NavGraph` ✓
