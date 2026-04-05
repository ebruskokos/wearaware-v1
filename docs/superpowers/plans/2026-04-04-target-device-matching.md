# Target Device Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Target Device Matching" feature that scores every nearby BLE device against a known profile (Wayfarer 00ZS) so the user can identify their specific Meta glasses among many BLE devices.

**Architecture:** New pure-Kotlin domain use case `MatchTargetDeviceUseCase` scores each `ObservedDevice` against a `TargetDeviceProfile` using name, classification, manufacturer rule, RSSI, and persistence signals. `ScanViewModel` calls it on every device update and exposes scores in `ScanUiState`. UI renders a best-match banner and highlights the top candidate in the device list.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (version bump to 2 with fallbackToDestructiveMigration), JUnit 4 unit tests.

---

## File Map

**New files:**
- `domain/model/TargetDeviceProfile.kt` — the known device we're searching for
- `domain/model/TargetMatchResult.kt` — scoring result for one device (includes `MatchConfidence` enum)
- `domain/usecase/MatchTargetDeviceUseCase.kt` — pure scoring logic + `DefaultTargetProfile` object
- `ui/components/TargetMatchBanner.kt` — "Best match" banner shown at top of ScanScreen
- `app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt` — 7 unit tests

**Modified files:**
- `domain/model/ScanLogEntry.kt` — add `targetMatchScore`, `targetMatchReason`, `isTopCandidate`
- `domain/repository/ScanLogRepository.kt` — add `matchResult: TargetMatchResult?` param to `log()`
- `domain/usecase/LogScanEventUseCase.kt` — pass `matchResult` through to repository
- `data/local/ScanLogEntity.kt` — add 3 new columns with defaults
- `data/local/WearAwareDatabase.kt` — bump version 1→2, add fallbackToDestructiveMigration
- `data/mapper/ScanLogMapper.kt` — map new fields
- `data/repository/ScanLogRepositoryImpl.kt` — pass `matchResult` to mapper
- `ui/viewmodel/ScanUiState.kt` — add `focusMode`, `deviceMatchScores`, computed `bestMatch` + `sortedDevices`
- `ui/viewmodel/ScanViewModel.kt` — inject `MatchTargetDeviceUseCase`, call it in `processDeviceUpdate`
- `ui/components/DeviceCard.kt` — add `isTopCandidate` param, highlight card + badge
- `ui/screens/ScanScreen.kt` — add `TargetMatchBanner` + focus mode toggle
- `ui/screens/DeviceDetailScreen.kt` — add "Target match analysis" section

---

## Task 1: Domain models — TargetDeviceProfile and TargetMatchResult

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/TargetDeviceProfile.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/TargetMatchResult.kt`

- [ ] **Step 1: Create TargetDeviceProfile.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Describes a known device the user wants to find among nearby BLE devices.
 *   Used by MatchTargetDeviceUseCase to score candidates.
 * NOTES: All fields are hints — BLE advertising may not expose all of them.
 *   friendlyName is the exact name shown in the Meta app (e.g. "Wayfarer 00ZS").
 *   modelHint and brandHint are used for partial matching.
 */
data class TargetDeviceProfile(
    val friendlyName: String,
    val modelHint: String,
    val brandHint: String,
    val category: DeviceCategory
)
```

- [ ] **Step 2: Create TargetMatchResult.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Result of scoring one ObservedDevice against a TargetDeviceProfile.
 *   score is additive — each matching signal contributes points.
 *   isTopCandidate is set by MatchTargetDeviceUseCase after comparing all devices.
 * NOTES: matchedSignals is a human-readable list of reasons, shown in DeviceDetailScreen.
 */
data class TargetMatchResult(
    val deviceId: String,
    val score: Int,
    val confidence: MatchConfidence,
    val matchedSignals: List<String>,
    val isTopCandidate: Boolean
)

enum class MatchConfidence {
    HIGH,    // score >= 50: strong evidence (exact name or name + manufacturer)
    MEDIUM,  // score >= 20: classification or manufacturer match
    LOW,     // score >= 5:  weak signal only (RSSI or persistence alone)
    NONE     // score < 5:   no useful match signals
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/TargetDeviceProfile.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/TargetMatchResult.kt
git commit -m "feat: add TargetDeviceProfile and TargetMatchResult domain models"
```

---

## Task 2: MatchTargetDeviceUseCase + unit tests (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCase.kt`
- Create: `app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt`:

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchTargetDeviceUseCaseTest {

    private val useCase = MatchTargetDeviceUseCase()
    private val profile = DefaultTargetProfile.WAYFARER_00ZS

    private fun makeDevice(
        id: String = "AA:BB:CC:DD:EE:FF",
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
    fun `exact name match gives score 50 or above and HIGH confidence`() {
        val device = makeDevice(name = "Wayfarer 00ZS")
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 50, was ${result.score}", result.score >= 50)
        assertEquals(MatchConfidence.HIGH, result.confidence)
        assertTrue(result.matchedSignals.any { it.contains("Exact name match") })
    }

    @Test
    fun `partial name match containing model hint gives medium or high confidence`() {
        val device = makeDevice(name = "Wayfarer 01AB")
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 20, was ${result.score}", result.score >= 20)
        assertTrue(result.matchedSignals.any { it.contains("Partial name match") })
    }

    @Test
    fun `manufacturer rule match alone gives MEDIUM confidence`() {
        val device = makeDevice(
            category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
            matchedRuleId = "meta_rayban_v1"
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue("score should be >= 20, was ${result.score}", result.score >= 20)
        assertTrue(result.matchedSignals.any { it.contains("Manufacturer match") })
        assertTrue(result.matchedSignals.any { it.contains("Classification") })
    }

    @Test
    fun `device with no matching signals gives NONE confidence and is not top candidate`() {
        val device = makeDevice(name = "LG TV Remote", category = DeviceCategory.UNKNOWN_BLE_DEVICE)
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertEquals(MatchConfidence.NONE, result.confidence)
        assertFalse(result.isTopCandidate)
        assertTrue(result.matchedSignals.isEmpty())
    }

    @Test
    fun `top candidate is the device with the highest score`() {
        val weak = makeDevice("id1", name = "Random BLE Device")
        val strong = makeDevice("id2", name = "Wayfarer 00ZS")
        val results = useCase(listOf(weak, strong), profile)
        assertFalse(results["id1"]!!.isTopCandidate)
        assertTrue(results["id2"]!!.isTopCandidate)
    }

    @Test
    fun `empty device list returns empty map`() {
        val results = useCase(emptyList(), profile)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `persistence bonus added when device seen for 30 seconds or more`() {
        val device = makeDevice(
            category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
            matchedRuleId = "meta_rayban_v1",
            seenDurationMs = 35_000L
        )
        val results = useCase(listOf(device), profile)
        val result = results[device.id]!!
        assertTrue(result.matchedSignals.any { it.contains("Persistent") })
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*.MatchTargetDeviceUseCaseTest" 2>&1 | tail -20
```

Expected: FAIL — `MatchTargetDeviceUseCase` and `DefaultTargetProfile` not found.

- [ ] **Step 3: Implement MatchTargetDeviceUseCase**

Create `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCase.kt`:

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Hardcoded target profile for the user's primary test device.
 * Wayfarer 00ZS is the device name shown in the Meta app.
 */
object DefaultTargetProfile {
    val WAYFARER_00ZS = TargetDeviceProfile(
        friendlyName = "Wayfarer 00ZS",
        modelHint = "Wayfarer",
        brandHint = "Meta",
        category = DeviceCategory.SMART_GLASSES
    )
}

/**
 * PURPOSE: Scores each ObservedDevice against a TargetDeviceProfile to identify
 *   the most likely candidate among nearby BLE devices.
 *
 * Scoring (additive):
 *   +50  exact advertised name match (e.g. "Wayfarer 00ZS")
 *   +25  partial name match containing modelHint (e.g. "Wayfarer")
 *   +15  partial name match containing any word from friendlyName (length > 2)
 *   +20  classification is CAMERA_CAPABLE_WEARABLE or SMART_GLASSES
 *   +15  matched rule ID contains "meta" or "rayban"
 *   +1–10 RSSI bonus (VERY_CLOSE=10, STRONG=7, NEARBY=4, WEAK=1)
 *   +5   device present for >= 30 seconds (persistence bonus)
 *
 * Confidence thresholds:
 *   HIGH   >= 50
 *   MEDIUM >= 20
 *   LOW    >= 5
 *   NONE   < 5
 *
 * NOTES: isTopCandidate is set only for the single highest-scoring device
 *   with confidence != NONE. If all devices have NONE confidence, no candidate is set.
 */
class MatchTargetDeviceUseCase @Inject constructor() {

    operator fun invoke(
        devices: List<ObservedDevice>,
        profile: TargetDeviceProfile
    ): Map<String, TargetMatchResult> {
        if (devices.isEmpty()) return emptyMap()

        val rawResults = devices.associate { it.id to scoreDevice(it, profile) }

        val topId = rawResults.values
            .filter { it.confidence != MatchConfidence.NONE }
            .maxByOrNull { it.score }
            ?.deviceId

        return rawResults.mapValues { (id, result) ->
            result.copy(isTopCandidate = id == topId && topId != null)
        }
    }

    private fun scoreDevice(device: ObservedDevice, profile: TargetDeviceProfile): TargetMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // --- Name matching ---
        val name = device.advertisedName
        if (name != null) {
            when {
                name.equals(profile.friendlyName, ignoreCase = true) -> {
                    score += 50
                    signals += "Exact name match: \"$name\""
                }
                name.contains(profile.modelHint, ignoreCase = true) -> {
                    score += 25
                    signals += "Partial name match (model hint): \"$name\""
                }
                profile.friendlyName.split(" ")
                    .filter { it.length > 2 }
                    .any { part -> name.contains(part, ignoreCase = true) } -> {
                    score += 15
                    signals += "Partial name match: \"$name\""
                }
            }
        }

        // --- Classification match ---
        if (device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE ||
            device.classification.category == DeviceCategory.SMART_GLASSES
        ) {
            score += 20
            signals += "Classification: ${device.classification.category.name}"
        }

        // --- Manufacturer rule match (Meta/Ray-Ban) ---
        val ruleId = device.classification.matchedRuleId?.lowercase() ?: ""
        if (ruleId.contains("meta") || ruleId.contains("rayban")) {
            score += 15
            signals += "Manufacturer match: Meta/Ray-Ban rule (${device.classification.matchedRuleId})"
        }

        // --- RSSI bonus ---
        val rssiBonus = when (device.proximityLabel) {
            ProximityLabel.VERY_CLOSE -> 10
            ProximityLabel.STRONG     -> 7
            ProximityLabel.NEARBY     -> 4
            ProximityLabel.WEAK       -> 1
            ProximityLabel.UNKNOWN    -> 0
        }
        if (rssiBonus > 0) {
            score += rssiBonus
            signals += "Signal: ${device.proximityLabel.name} (+$rssiBonus)"
        }

        // --- Persistence bonus ---
        if (device.seenDurationMs >= 30_000L) {
            score += 5
            signals += "Persistent: present for ${device.seenDurationMs / 1000}s"
        }

        val confidence = when {
            score >= 50 -> MatchConfidence.HIGH
            score >= 20 -> MatchConfidence.MEDIUM
            score >= 5  -> MatchConfidence.LOW
            else        -> MatchConfidence.NONE
        }

        return TargetMatchResult(
            deviceId = device.id,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            isTopCandidate = false   // set by invoke() after comparing all devices
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*.MatchTargetDeviceUseCaseTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCase.kt \
        app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt
git commit -m "feat: add MatchTargetDeviceUseCase with 7 unit tests"
```

---

## Task 3: Update ScanUiState and ScanViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`

- [ ] **Step 1: Replace ScanUiState.kt**

Full new content:

```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.TargetMatchResult

data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    val activeAlert: PersistenceAlert? = null,
    val appVersion: String = "",
    val ruleSetVersion: String = "",
    val ruleSetHash: String = "",
    val focusMode: Boolean = false,
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap()
) {
    /** The highest-scoring candidate paired with its device, or null if no match found. */
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull { it.isTopCandidate } ?: return null
            val device = devices.find { it.id == top.deviceId } ?: return null
            return device to top
        }

    /**
     * In focus mode: sorted by target match score descending (best candidate first).
     * Otherwise: original order (sorted by signal strength from BleRepositoryImpl).
     */
    val sortedDevices: List<ObservedDevice>
        get() = if (focusMode && deviceMatchScores.isNotEmpty()) {
            devices.sortedByDescending { deviceMatchScores[it.id]?.score ?: 0 }
        } else {
            devices
        }
}

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
```

- [ ] **Step 2: Update ScanViewModel.kt**

Add `MatchTargetDeviceUseCase` injection and call it in `processDeviceUpdate`. Replace the full file:

```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.usecase.*
import com.wearaware.app.util.AboutInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val observeScannedDevices: ObserveScannedDevicesUseCase,
    private val evaluatePersistence: EvaluatePersistenceUseCase,
    private val logScanEvent: LogScanEventUseCase,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val bleRepository: BleRepository,
    aboutInfo: AboutInfo
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val lastAlertedAt = mutableMapOf<String, Long>()
    private val loggedDeviceIds = mutableSetOf<String>()

    init {
        _uiState.update {
            it.copy(
                appVersion = aboutInfo.appVersion,
                ruleSetVersion = aboutInfo.ruleSetVersion,
                ruleSetHash = aboutInfo.ruleSetHash
            )
        }
    }

    fun startScanning() {
        if (!bleRepository.isBleAvailable) {
            _uiState.update { it.copy(scanState = ScanState.BLUETOOTH_UNAVAILABLE) }
            return
        }
        bleRepository.startScanning()
        _uiState.update { it.copy(scanState = ScanState.SCANNING) }

        viewModelScope.launch {
            observeScannedDevices().collect { devices ->
                processDeviceUpdate(devices)
            }
        }
    }

    fun stopScanning() {
        bleRepository.stopScanning()
        lastAlertedAt.clear()
        loggedDeviceIds.clear()
        _uiState.update {
            it.copy(
                scanState = ScanState.STOPPED,
                devices = emptyList(),
                activeAlert = null,
                deviceMatchScores = emptyMap()
            )
        }
    }

    fun setPermissionsRequired() {
        if (_uiState.value.scanState != ScanState.SCANNING) {
            _uiState.update { it.copy(scanState = ScanState.PERMISSIONS_REQUIRED) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(activeAlert = null) }
    }

    fun toggleFocusMode() {
        _uiState.update { it.copy(focusMode = !it.focusMode) }
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    private fun processDeviceUpdate(devices: List<ObservedDevice>) {
        // 1. Compute target match scores for all devices
        val matchScores = matchTargetDevice(devices, DefaultTargetProfile.WAYFARER_00ZS)

        // 2. Log each newly detected device once per session (with match score)
        devices
            .filter { it.id !in loggedDeviceIds && it.visibilityState == VisibilityState.DETECTED_NOW }
            .forEach { device ->
                loggedDeviceIds.add(device.id)
                viewModelScope.launch { logScanEvent(device, matchScores[device.id]) }
            }

        // 3. Evaluate persistence alerts
        val newAlerts = devices.mapNotNull { device ->
            evaluatePersistence(device, lastAlertedAt)?.also { alert ->
                val key = "${device.id}:${alert.alertType.name}"
                lastAlertedAt[key] = alert.triggeredAt
            }
        }

        val currentAlert = _uiState.value.activeAlert
        val updatedAlert: PersistenceAlert? = when {
            currentAlert != null && deviceIsSignalLost(devices, currentAlert.deviceId) -> null
            newAlerts.isNotEmpty() -> newAlerts.maxByOrNull { it.triggeredAt }
            else -> currentAlert
        }

        _uiState.update {
            it.copy(
                devices = devices,
                activeAlert = updatedAlert,
                deviceMatchScores = matchScores
            )
        }
    }

    private fun deviceIsSignalLost(devices: List<ObservedDevice>, deviceId: String): Boolean =
        devices.find { it.id == deviceId }?.visibilityState == VisibilityState.SIGNAL_LOST

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopScanning()
    }
}
```

- [ ] **Step 3: Run full unit test suite to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt
git commit -m "feat: add focusMode and deviceMatchScores to ScanUiState, wire MatchTargetDeviceUseCase in ScanViewModel"
```

---

## Task 4: TargetMatchBanner component

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/TargetMatchBanner.kt`

- [ ] **Step 1: Create TargetMatchBanner.kt**

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.TargetMatchResult
import com.wearaware.app.util.formatDuration

/**
 * PURPOSE: Displays the best-matching candidate for the user's target device
 *   (Wayfarer 00ZS) at the top of ScanScreen.
 * NOTES: Shows "No strong match" when bestMatch is null or top candidate has LOW/NONE
 *   confidence. Never claims certainty — only shows the best candidate found so far.
 */
@Composable
fun TargetMatchBanner(
    bestMatch: Pair<ObservedDevice, TargetMatchResult>?,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Best match for: Wayfarer 00ZS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (bestMatch == null || bestMatch.second.confidence == MatchConfidence.NONE) {
                Text(
                    text = "No strong match for your target device yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                val (device, match) = bestMatch
                Text(
                    text = device.advertisedName
                        ?: match.matchedSignals.firstOrNull()
                        ?: device.classification.displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "MAC: ${device.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (device.classification.matchedRuleId != null) {
                    Text(
                        text = "Rule: ${device.classification.matchedRuleId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "${device.classification.displayLabel} • ${match.confidence.name} (score: ${match.score})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Signal: ${device.proximityLabel.name} • ${device.averagedRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Matched signals:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                match.matchedSignals.forEach { signal ->
                    Text(
                        text = "  • $signal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDeviceClick(device.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Device Details")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/TargetMatchBanner.kt
git commit -m "feat: add TargetMatchBanner component"
```

---

## Task 5: Update DeviceCard (highlight top candidate) + ScanScreen

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`

- [ ] **Step 1: Update DeviceCard.kt**

Add `isTopCandidate: Boolean = false` param and highlight the card:

Read the current DeviceCard.kt first, then replace the full file with:

```kotlin
package com.wearaware.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.util.formatDuration

@Composable
fun DeviceCard(
    device: ObservedDevice,
    onClick: () -> Unit,
    isTopCandidate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isTopCandidate)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isTopCandidate) {
                Text(
                    text = "★ Best candidate for Wayfarer 00ZS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SignalBars(proximity = device.proximityLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                    VisibilityBadge(state = device.visibilityState)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.advertisedName
                            ?: device.classification.matchedRuleId?.let { device.classification.displayLabel }
                            ?: "BLE Device",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = device.classification.displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = device.proximityLabel.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Seen: ${device.seenDurationMs.formatDuration()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View device details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Update ScanScreen.kt**

Read current `ScanScreen.kt`, then make these changes:

1. Add `TargetMatchBanner` call after the alert banner
2. Add focus mode toggle button in the top app bar actions
3. Use `uiState.sortedDevices` instead of `uiState.devices` for the list
4. Pass `isTopCandidate` to each `DeviceCard`

Replace the full file:

```kotlin
package com.wearaware.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wearaware.app.ui.components.*
import com.wearaware.app.ui.viewmodel.ScanState
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionUtils.BLE_PERMISSIONS.toList()
    ) { results ->
        if (PermissionUtils.allGranted(results)) viewModel.startScanning()
        else viewModel.setPermissionsRequired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WearAware") },
                actions = {
                    if (uiState.scanState == ScanState.SCANNING) {
                        TextButton(onClick = { viewModel.toggleFocusMode() }) {
                            Text(
                                text = if (uiState.focusMode) "All Devices" else "Focus Mode",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Alert banner
            val alert = uiState.activeAlert
            if (alert != null) {
                AlertBanner(
                    alert = alert,
                    onDismiss = { viewModel.dismissAlert() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Bluetooth off banner
            if (uiState.scanState == ScanState.BLUETOOTH_UNAVAILABLE) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bluetooth is turned off",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }) {
                            Text("Turn on Bluetooth")
                        }
                    }
                }
            }

            // Target match banner (shown while scanning)
            if (uiState.scanState == ScanState.SCANNING) {
                TargetMatchBanner(
                    bestMatch = uiState.bestMatch,
                    onDeviceClick = onDeviceClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Scan status header
            ScanStatusHeader(
                scanState = uiState.scanState,
                deviceCount = uiState.devices.size
            )

            // Focus mode label
            if (uiState.focusMode && uiState.scanState == ScanState.SCANNING) {
                Text(
                    text = "Sorted by match score for Wayfarer 00ZS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp)
                )
            }

            // Device list
            if (uiState.devices.isEmpty() && uiState.scanState == ScanState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No nearby devices detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.sortedDevices,
                        key = { it.id }
                    ) { device ->
                        val matchResult = uiState.deviceMatchScores[device.id]
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) },
                            isTopCandidate = matchResult?.isTopCandidate == true
                        )
                    }
                }
            }

            // Scan control button
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    when {
                        uiState.scanState == ScanState.SCANNING -> viewModel.stopScanning()
                        permissionsState.allPermissionsGranted -> viewModel.startScanning()
                        else -> permissionsState.launchMultiplePermissionRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (uiState.scanState == ScanState.SCANNING) "Stop Scan" else "Start Scan")
            }

            // Disclaimer
            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
    }
}
```

- [ ] **Step 3: Run full unit tests to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt \
        app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt
git commit -m "feat: highlight top candidate in DeviceCard, add TargetMatchBanner and focus mode toggle to ScanScreen"
```

---

## Task 6: Update DeviceDetailScreen with target match analysis

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`

- [ ] **Step 1: Read current DeviceDetailScreen.kt to see existing content**

Read: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`

- [ ] **Step 2: Add target match analysis section**

The `viewModel.uiState.deviceMatchScores[deviceId]` gives the `TargetMatchResult` for this device.

After the "Alert History" section and before the final "Disclaimer" section, insert:

```kotlin
// Target match analysis
HorizontalDivider()
Text("Target Match Analysis", style = MaterialTheme.typography.titleSmall)
Text(
    text = "Target: Wayfarer 00ZS",
    style = MaterialTheme.typography.bodySmall
)

val matchResult = uiState.deviceMatchScores[deviceId]
if (matchResult == null || matchResult.score == 0) {
    Text(
        text = "No match signals found for this device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
} else {
    Text(
        text = "Match score: ${matchResult.score} • Confidence: ${matchResult.confidence.name}",
        style = MaterialTheme.typography.bodySmall
    )
    if (matchResult.isTopCandidate) {
        Text(
            text = "★ This is currently the best candidate for Wayfarer 00ZS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (matchResult.matchedSignals.isNotEmpty()) {
        Text("Matched signals:", style = MaterialTheme.typography.labelSmall)
        matchResult.matchedSignals.forEach { signal ->
            Text(
                text = "  • $signal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

Also add this import at the top of the file:
```kotlin
import com.wearaware.app.domain.model.MatchConfidence
```

- [ ] **Step 3: Run full unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt
git commit -m "feat: add target match analysis section to DeviceDetailScreen"
```

---

## Task 7: Update logging chain (ScanLogEntry, ScanLogEntity, ScanLogMapper, ScanLogRepository, ScanLogRepositoryImpl, LogScanEventUseCase, WearAwareDatabase)

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/repository/ScanLogRepository.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/usecase/LogScanEventUseCase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt`

- [ ] **Step 1: Update ScanLogEntry.kt** — add 3 nullable fields with defaults

```kotlin
package com.wearaware.app.domain.model

data class ScanLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?,
    val targetMatchScore: Int? = null,
    val targetMatchReason: String? = null,
    val isTopCandidate: Boolean = false
)
```

- [ ] **Step 2: Update ScanLogRepository.kt** — add `matchResult` param to `log()`

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult
import kotlinx.coroutines.flow.Flow

interface ScanLogRepository {
    suspend fun log(device: ObservedDevice, matchResult: TargetMatchResult? = null)
    fun observeAll(): Flow<List<ScanLogEntry>>
    suspend fun clearAll()
}
```

- [ ] **Step 3: Update LogScanEventUseCase.kt** — pass `matchResult` through

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.TargetMatchResult
import com.wearaware.app.domain.repository.ScanLogRepository
import javax.inject.Inject

class LogScanEventUseCase @Inject constructor(
    private val scanLogRepository: ScanLogRepository
) {
    suspend operator fun invoke(device: ObservedDevice, matchResult: TargetMatchResult? = null) {
        scanLogRepository.log(device, matchResult)
    }
}
```

- [ ] **Step 4: Update ScanLogEntity.kt** — add 3 new columns

```kotlin
package com.wearaware.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_log")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?,
    @ColumnInfo(defaultValue = "NULL") val targetMatchScore: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val targetMatchReason: String? = null,
    @ColumnInfo(defaultValue = "0") val isTopCandidate: Boolean = false
)
```

- [ ] **Step 5: Update WearAwareDatabase.kt** — bump version to 2, add fallbackToDestructiveMigration

```kotlin
package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate to scan_log
 * NOTES: fallbackToDestructiveMigration drops and recreates the DB on schema change.
 *   Session logs are ephemeral (user-clearable), so this is acceptable for v1.
 */
@Database(entities = [ScanLogEntity::class], version = 2, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 6: Update DatabaseModule.kt** — add fallbackToDestructiveMigration to the builder

Read `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt` first, then add `.fallbackToDestructiveMigration()` to the `Room.databaseBuilder(...)` call, before `.build()`.

It should look like:
```kotlin
Room.databaseBuilder(context, WearAwareDatabase::class.java, WearAwareDatabase.DATABASE_NAME)
    .fallbackToDestructiveMigration()
    .build()
```

- [ ] **Step 7: Update ScanLogMapper.kt** — accept optional `TargetMatchResult`

```kotlin
package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult

fun ObservedDevice.toScanLogEntity(matchResult: TargetMatchResult? = null): ScanLogEntity = ScanLogEntity(
    timestamp = System.currentTimeMillis(),
    deviceId = id,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel.name,
    visibilityState = visibilityState.name,
    matchedRuleId = classification.matchedRuleId,
    ruleVersion = classification.ruleVersion,
    category = classification.category.name,
    confidence = classification.confidence.name,
    evaluationNotes = classification.evaluationNotes,
    targetMatchScore = matchResult?.score,
    targetMatchReason = matchResult?.matchedSignals?.joinToString("; "),
    isTopCandidate = matchResult?.isTopCandidate ?: false
)

fun ScanLogEntity.toDomain(): ScanLogEntry = ScanLogEntry(
    id = id,
    timestamp = timestamp,
    deviceId = deviceId,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel,
    visibilityState = visibilityState,
    matchedRuleId = matchedRuleId,
    ruleVersion = ruleVersion,
    category = category,
    confidence = confidence,
    evaluationNotes = evaluationNotes,
    targetMatchScore = targetMatchScore,
    targetMatchReason = targetMatchReason,
    isTopCandidate = isTopCandidate
)
```

- [ ] **Step 8: Update ScanLogRepositoryImpl.kt** — pass `matchResult` to mapper

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.mapper.toScanLogEntity
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult
import com.wearaware.app.domain.repository.ScanLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanLogRepositoryImpl @Inject constructor(
    private val dao: ScanLogDao
) : ScanLogRepository {

    override suspend fun log(device: ObservedDevice, matchResult: TargetMatchResult?) {
        dao.insert(device.toScanLogEntity(matchResult))
    }

    override fun observeAll(): Flow<List<ScanLogEntry>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
```

- [ ] **Step 9: Run full unit test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit all logging chain changes**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt \
        app/src/main/kotlin/com/wearaware/app/domain/repository/ScanLogRepository.kt \
        app/src/main/kotlin/com/wearaware/app/domain/usecase/LogScanEventUseCase.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt \
        app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt \
        app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt
git commit -m "feat: include targetMatchScore, targetMatchReason, isTopCandidate in session log (DB v2)"
```

---

## Task 8: Final build and install verification

- [ ] **Step 1: Run full unit test suite one more time**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. Should show at least 55 tests passing (48 original + 7 new).

- [ ] **Step 2: Build and install on device**

```bash
./gradlew installDebug
```

Expected: BUILD SUCCESSFUL, installed on connected device.

- [ ] **Step 3: Manual verification on device**

With Poco X3 connected and Meta glasses powered on:
1. Open WearAware → tap Start Scan → grant permissions
2. Confirm "Best match for: Wayfarer 00ZS" banner appears at top
3. If glasses are nearby and broadcasting:
   - If name "Wayfarer 00ZS" is in advertisement → banner shows HIGH confidence
   - If manufacturer ID 0x0075 (Meta) is in advertisement → banner shows MEDIUM confidence
4. Tap the highlighted card (★ Best candidate) → DeviceDetailScreen shows "Target Match Analysis" section
5. Tap "Focus Mode" in toolbar → device list re-sorts by match score

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: target device matching feature complete — 55+ tests, verified on device"
```
