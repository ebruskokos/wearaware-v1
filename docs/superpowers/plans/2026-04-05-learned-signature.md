# Learned Signature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users tap "Learn this device" on the top compare candidate and have the app show "My Meta Glasses" as the primary label on all future scans — live and compare — using weighted BLE signal matching rather than exact fingerprint matching.

**Architecture:** A new `LearnedSignatureRepository` (SharedPreferences + Gson) persists one `LearnedDeviceSignature`. A `MatchLearnedSignatureUseCase` scores candidate devices against the saved signature using manufacturer data prefixes, IDs, service UUIDs, and behavior signals. Both `CaptureViewModel` and `ScanViewModel` load the signature on init and update `learnedMatchResults` state, which drives label overrides and debug sections in the UI.

**Tech Stack:** Kotlin, Hilt, Jetpack Compose, Room (existing), SharedPreferences, Gson (existing), MockK, kotlinx-coroutines-test

---

## File Map

**New files:**
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedDeviceSignature.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchInput.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchResult.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/repository/LearnedSignatureRepository.kt`
- `app/src/main/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImpl.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCase.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCase.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCase.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImplTest.kt`

**Modified files:**
- `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt` — provide SharedPreferences + Gson
- `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt` — bind LearnedSignatureRepository
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt` — add learned fields
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt` — add learned fields
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt` — learnDevice, clearLearnedDevice, matching
- `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt` — reloadLearnedSignature, matching
- `app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt` — status banner, label override, learn button, snackbar
- `app/src/main/kotlin/com/wearaware/app/ui/screens/CapturedDeviceDetailScreen.kt` — learn button, debug section
- `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt` — status chip, reloadLearnedSignature call
- `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt` — learned label override

---

### Task 1: Domain models

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedDeviceSignature.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchInput.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchResult.kt`

- [ ] **Step 1: Create LearnedDeviceSignature.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * Persisted BLE signal signature for a device the user has confirmed as their glasses.
 * Stored via SharedPreferences + Gson. One record at a time.
 *
 * DESIGN: No MAC address (randomized). Behavior signals (RSSI, seenCount) are
 * session-specific and computed fresh at match time — not stored here.
 */
data class LearnedDeviceSignature(
    val displayName: String,                     // always "My Meta Glasses"
    val savedAt: Long,                           // epoch millis
    val fingerprintId: String,                   // bonus signal only — never required for match
    val manufacturerIds: List<Int>,              // strong match signal
    val manufacturerDataPrefixes: List<String>,  // very strong signal: "companyIdHex:first4bytesHex"
    val serviceUuids: List<String>               // moderate match signal
)
```

- [ ] **Step 2: Create LearnedMatchInput.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * Neutral input type for MatchLearnedSignatureUseCase.
 * Allows the use case to work with both CapturedDevice (compare flow) and
 * ObservedDevice (live scan flow) without importing either model.
 */
data class LearnedMatchInput(
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    val manufacturerDataPrefixes: List<String>,  // same format as LearnedDeviceSignature
    val serviceUuids: List<String>,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean
)
```

- [ ] **Step 3: Create LearnedMatchResult.kt**

```kotlin
package com.wearaware.app.domain.model

enum class LearnedConfidence { STRONG, POSSIBLE, NONE }

/**
 * Output of MatchLearnedSignatureUseCase for a single candidate device.
 * labelOverrideActive is true for STRONG and POSSIBLE — both get a label override.
 */
data class LearnedMatchResult(
    val signature: LearnedDeviceSignature,
    val score: Int,
    val confidence: LearnedConfidence,
    val matchedSignals: List<String>,    // human-readable reasons for debug UI
    val labelOverrideActive: Boolean     // true when confidence != NONE
)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/LearnedDeviceSignature.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchInput.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchResult.kt
git commit -m "feat: learned-signature domain models"
```

---

### Task 2: Repository interface, implementation, and DI

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/LearnedSignatureRepository.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt`

- [ ] **Step 1: Create LearnedSignatureRepository.kt**

```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.LearnedDeviceSignature

interface LearnedSignatureRepository {
    fun load(): LearnedDeviceSignature?
    fun save(signature: LearnedDeviceSignature)
    fun clear()
}
```

- [ ] **Step 2: Create LearnedSignatureRepositoryImpl.kt**

```kotlin
package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_LEARNED_SIGNATURE = "learned_device_signature"

@Singleton
class LearnedSignatureRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : LearnedSignatureRepository {

    override fun load(): LearnedDeviceSignature? {
        val json = prefs.getString(KEY_LEARNED_SIGNATURE, null) ?: return null
        return runCatching { gson.fromJson(json, LearnedDeviceSignature::class.java) }.getOrNull()
    }

    override fun save(signature: LearnedDeviceSignature) {
        prefs.edit().putString(KEY_LEARNED_SIGNATURE, gson.toJson(signature)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_LEARNED_SIGNATURE).apply()
    }
}
```

- [ ] **Step 3: Add SharedPreferences and Gson providers to DatabaseModule.kt**

Open `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`. Add these imports at the top:

```kotlin
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
```

Add these two provider methods inside the `DatabaseModule` object, after the existing `provideCaptureDao` method:

```kotlin
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("wearaware_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
```

- [ ] **Step 4: Bind LearnedSignatureRepository in RepositoryModule.kt**

Open `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt`. Add this import:

```kotlin
import com.wearaware.app.data.repository.LearnedSignatureRepositoryImpl
import com.wearaware.app.domain.repository.LearnedSignatureRepository
```

Add this binding inside the `RepositoryModule` abstract class, after `bindCaptureRepository`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindLearnedSignatureRepository(
        impl: LearnedSignatureRepositoryImpl
    ): LearnedSignatureRepository
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/repository/LearnedSignatureRepository.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImpl.kt \
        app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt \
        app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt
git commit -m "feat: learned-signature repository — SharedPreferences + Gson"
```

---

### Task 3: Repository tests

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImplTest.kt`

- [ ] **Step 1: Write all repository tests**

```kotlin
package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.LearnedDeviceSignature
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LearnedSignatureRepositoryImplTest {

    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val gson = Gson()
    private lateinit var repo: LearnedSignatureRepositoryImpl

    private val testSignature = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-abc123",
        manufacturerIds = listOf(0x01AB),
        manufacturerDataPrefixes = listOf("01ab:deadbeef"),
        serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
    )

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        repo = LearnedSignatureRepositoryImpl(prefs, gson)
    }

    @Test
    fun `load returns null when key is missing`() {
        every { prefs.getString("learned_device_signature", null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when JSON is malformed`() {
        every { prefs.getString("learned_device_signature", null) } returns "not-json"
        assertNull(repo.load())
    }

    @Test
    fun `save then load roundtrip returns identical signature`() {
        val stored = slot<String>()
        every { editor.putString("learned_device_signature", capture(stored)) } returns editor

        repo.save(testSignature)

        every { prefs.getString("learned_device_signature", null) } returns stored.captured
        val loaded = repo.load()

        assertEquals(testSignature, loaded)
    }

    @Test
    fun `clear removes the stored key`() {
        repo.clear()
        verify { editor.remove("learned_device_signature") }
        verify { editor.apply() }
    }

    @Test
    fun `save persists JSON and calls apply`() {
        repo.save(testSignature)
        verify { editor.putString("learned_device_signature", any()) }
        verify { editor.apply() }
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest --tests "com.wearaware.app.data.repository.LearnedSignatureRepositoryImplTest" 2>&1 | tail -20
```

Expected: All 5 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImplTest.kt
git commit -m "test: LearnedSignatureRepositoryImpl — save/load/clear contract"
```

---

### Task 4: MatchLearnedSignatureUseCase (TDD)

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCaseTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCase.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchLearnedSignatureUseCaseTest {

    private val useCase = MatchLearnedSignatureUseCase()

    private fun makeSignature(
        fingerprintId: String = "saved-fp",
        manufacturerIds: List<Int> = listOf(0x01AB),
        prefixes: List<String> = listOf("01ab:deadbeef"),
        serviceUuids: List<String> = listOf("uuid-glasses")
    ) = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1000L,
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids
    )

    private fun makeInput(
        fingerprintId: String = "candidate-fp",
        manufacturerIds: List<Int> = emptyList(),
        prefixes: List<String> = emptyList(),
        serviceUuids: List<String> = emptyList(),
        averageRssi: Int = -80,
        seenCount: Int = 1,
        visibleAtStop: Boolean = false
    ) = LearnedMatchInput(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop
    )

    @Test
    fun `manufacturer data prefix + manufacturer ID overlap → STRONG with label override`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x01AB),
                prefixes = listOf("01ab:deadbeef")   // matches saved prefix
            ),
            makeSignature()
        )
        // +5 prefix + +4 mfrId = 9 → STRONG
        assertEquals(LearnedConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
        assertTrue(result.score >= 8)
    }

    @Test
    fun `manufacturer ID overlap only → POSSIBLE`() {
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x01AB)),
            makeSignature(prefixes = emptyList())  // no prefixes saved
        )
        // +4 mfrId only = 4 → POSSIBLE
        assertEquals(LearnedConfidence.POSSIBLE, result.confidence)
        assertTrue(result.labelOverrideActive)
    }

    @Test
    fun `no signal overlap → NONE, no label override`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x004C),  // Apple, not in signature
                prefixes = listOf("004c:aabbccdd")
            ),
            makeSignature()
        )
        assertEquals(LearnedConfidence.NONE, result.confidence)
        assertFalse(result.labelOverrideActive)
    }

    @Test
    fun `fingerprintId exact match alone does not reach STRONG`() {
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),  // matches, +3
            makeSignature(manufacturerIds = emptyList(), prefixes = emptyList(), serviceUuids = emptyList())
        )
        // +3 fingerprintId only = 3 → NONE (below 4)
        assertEquals(LearnedConfidence.NONE, result.confidence)
    }

    @Test
    fun `behavior signals contribute but cannot alone reach STRONG`() {
        // No identity overlap — only behavior signals
        val result = useCase(
            makeInput(
                manufacturerIds = emptyList(),
                prefixes = emptyList(),
                serviceUuids = emptyList(),
                averageRssi = -55,   // +2
                seenCount = 60,      // +2
                visibleAtStop = true // +1
            ),
            makeSignature(manufacturerIds = emptyList(), prefixes = emptyList(), serviceUuids = emptyList())
        )
        // +2 +2 +1 = 5 → POSSIBLE (not STRONG)
        assertEquals(LearnedConfidence.POSSIBLE, result.confidence)
        assertFalse(result.score >= 8)
    }

    @Test
    fun `service UUID overlap gives points`() {
        val result = useCase(
            makeInput(serviceUuids = listOf("uuid-glasses", "uuid-other")),
            makeSignature(
                manufacturerIds = emptyList(),
                prefixes = emptyList(),
                serviceUuids = listOf("uuid-glasses")
            )
        )
        // +2 UUID overlap = 2 → NONE (below 4) but score reflects it
        assertTrue(result.score >= 2)
    }

    @Test
    fun `fingerprintId match is included in signals list`() {
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),
            makeSignature()
        )
        assertTrue(result.matchedSignals.any { it.contains("FingerprintId") })
    }

    @Test
    fun `STRONG confidence with full signal overlap produces correct label`() {
        val sig = makeSignature()
        val result = useCase(
            makeInput(
                fingerprintId = "saved-fp",
                manufacturerIds = listOf(0x01AB),
                prefixes = listOf("01ab:deadbeef"),
                serviceUuids = listOf("uuid-glasses"),
                averageRssi = -55,
                seenCount = 60,
                visibleAtStop = true
            ),
            sig
        )
        assertEquals("My Meta Glasses", result.signature.displayName)
        assertEquals(LearnedConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest --tests "com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCaseTest" 2>&1 | tail -20
```

Expected: compilation error — `MatchLearnedSignatureUseCase` does not exist yet.

- [ ] **Step 3: Create MatchLearnedSignatureUseCase.kt**

```kotlin
package com.wearaware.app.domain.usecase

import android.util.Log
import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Scores a candidate device against a saved LearnedDeviceSignature.
 *
 * SCORING:
 *   +5 per manufacturer data prefix match ("companyIdHex:first4bytesHex")
 *   +4 if any manufacturerId overlaps (flat bonus)
 *   +3 if fingerprintId exactly matches (bonus — never a gate)
 *   +2 per service UUID overlap
 *   +2 if averageRssi >= -60 dBm
 *   +2 if seenCount >= 50
 *   +1 if visibleAtStop = true
 *
 * THRESHOLDS: STRONG >= 8 | POSSIBLE >= 4 | NONE < 4
 */
class MatchLearnedSignatureUseCase @Inject constructor() {

    operator fun invoke(
        input: LearnedMatchInput,
        signature: LearnedDeviceSignature
    ): LearnedMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // +5 per manufacturer data prefix match
        val matchedPrefixes = input.manufacturerDataPrefixes
            .intersect(signature.manufacturerDataPrefixes.toSet())
        for (prefix in matchedPrefixes) {
            score += 5
            signals += "Manufacturer data prefix match: $prefix"
        }

        // +4 flat bonus for any manufacturerId overlap
        val sharedIds = input.manufacturerIds.intersect(signature.manufacturerIds.toSet())
        if (sharedIds.isNotEmpty()) {
            score += 4
            val hex = sharedIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            signals += "Manufacturer ID overlap: $hex"
        }

        // +3 fingerprintId exact match (bonus only)
        if (input.fingerprintId == signature.fingerprintId) {
            score += 3
            signals += "FingerprintId exact match (bonus)"
        }

        // +2 per service UUID overlap
        val sharedUuids = input.serviceUuids.intersect(signature.serviceUuids.toSet())
        for (uuid in sharedUuids) {
            score += 2
            signals += "Service UUID match: $uuid"
        }

        // Behavior signals — computed fresh, not stored in signature
        if (input.averageRssi >= -60) {
            score += 2
            signals += "Close proximity: ${input.averageRssi} dBm"
        }
        if (input.seenCount >= 50) {
            score += 2
            signals += "High persistence: ${input.seenCount} observations"
        }
        if (input.visibleAtStop) {
            score += 1
            signals += "Still visible at capture stop"
        }

        val confidence = when {
            score >= 8 -> LearnedConfidence.STRONG
            score >= 4 -> LearnedConfidence.POSSIBLE
            else       -> LearnedConfidence.NONE
        }

        Log.d(
            "LearnedMatch",
            "candidate=${input.fingerprintId} score=$score confidence=$confidence labelOverride=${confidence != LearnedConfidence.NONE}"
        )

        return LearnedMatchResult(
            signature = signature,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            labelOverrideActive = confidence != LearnedConfidence.NONE
        )
    }
}

// --- Extraction helpers ---
// These convert raw device data into the prefix format used by LearnedDeviceSignature.

/**
 * Extracts manufacturer data prefixes from a CapturedDevice.manufacturerDataSummary string.
 * Format of summary: "01ab:deadbeef01234567,004c:aabbccddee"
 * Prefix format returned: "01ab:deadbeef" (company ID hex + colon + first 8 hex chars of data = 4 bytes)
 */
fun extractPrefixesFromSummary(manufacturerDataSummary: String?): List<String> {
    if (manufacturerDataSummary.isNullOrBlank()) return emptyList()
    return manufacturerDataSummary.split(",").mapNotNull { entry ->
        val colonIdx = entry.indexOf(':')
        if (colonIdx == -1) return@mapNotNull null
        val companyId = entry.substring(0, colonIdx).trim()
        val dataHex = entry.substring(colonIdx + 1).trim().take(8)
        if (dataHex.length >= 4) "$companyId:$dataHex" else null
    }
}

/**
 * Extracts manufacturer data prefixes from an ObservedDevice.fingerprint?.manufacturerDataHex map.
 * Map key: Int Company ID. Map value: full hex string of data.
 * Prefix format returned: "01ab:deadbeef" (same format as extractPrefixesFromSummary)
 */
fun extractPrefixesFromFingerprintMap(map: Map<Int, String>?): List<String> {
    if (map.isNullOrEmpty()) return emptyList()
    return map.entries.mapNotNull { (id, hex) ->
        val idHex = id.toString(16).padStart(4, '0')
        val prefix = hex.take(8)
        if (prefix.length >= 4) "$idHex:$prefix" else null
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest --tests "com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCaseTest" 2>&1 | tail -20
```

Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCase.kt \
        app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCaseTest.kt
git commit -m "feat: MatchLearnedSignatureUseCase — weighted BLE signal scoring"
```

---

### Task 5: SaveLearnedSignatureUseCase and ClearLearnedSignatureUseCase (TDD)

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCaseTest.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCaseTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCase.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCase.kt`

- [ ] **Step 1: Write failing tests for SaveLearnedSignatureUseCase**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class SaveLearnedSignatureUseCaseTest {

    private val repo = mockk<LearnedSignatureRepository>(relaxed = true)
    private val useCase = SaveLearnedSignatureUseCase(repo)

    private fun makeDevice(
        fingerprintId: String = "fp-abc",
        manufacturerIds: List<Int> = listOf(0x01AB),
        manufacturerDataSummary: String? = "01ab:deadbeef01234567",
        serviceUuids: List<String> = listOf("uuid-x")
    ) = CapturedDevice(
        fingerprintId = fingerprintId,
        advertisedName = null,
        macAddress = null,
        manufacturerIds = manufacturerIds,
        manufacturerDataSummary = manufacturerDataSummary,
        serviceUuids = serviceUuids,
        category = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames = emptyList(),
        firstSeenInCapture = 0L,
        lastSeenInCapture = 0L,
        peakRssi = -57,
        averageRssi = -57,
        seenCount = 128,
        visibleAtStop = true,
        targetMatchScore = null,
        targetMatchSignals = emptyList()
    )

    @Test
    fun `saves signature with correct displayName`() {
        useCase(makeDevice())
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals("My Meta Glasses", slot.captured.displayName)
    }

    @Test
    fun `saves fingerprintId from device`() {
        useCase(makeDevice(fingerprintId = "fp-abc"))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals("fp-abc", slot.captured.fingerprintId)
    }

    @Test
    fun `saves manufacturerIds from device`() {
        useCase(makeDevice(manufacturerIds = listOf(0x01AB)))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf(0x01AB), slot.captured.manufacturerIds)
    }

    @Test
    fun `extracts manufacturer data prefix from summary`() {
        useCase(makeDevice(manufacturerDataSummary = "01ab:deadbeef01234567"))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf("01ab:deadbeef"), slot.captured.manufacturerDataPrefixes)
    }

    @Test
    fun `saves serviceUuids from device`() {
        useCase(makeDevice(serviceUuids = listOf("uuid-x")))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertEquals(listOf("uuid-x"), slot.captured.serviceUuids)
    }

    @Test
    fun `null manufacturerDataSummary produces empty prefixes`() {
        useCase(makeDevice(manufacturerDataSummary = null))
        val slot = slot<LearnedDeviceSignature>()
        verify { repo.save(capture(slot)) }
        assertTrue(slot.captured.manufacturerDataPrefixes.isEmpty())
    }

    @Test
    fun `repository save is called exactly once`() {
        useCase(makeDevice())
        verify(exactly = 1) { repo.save(any()) }
    }
}
```

- [ ] **Step 2: Write failing tests for ClearLearnedSignatureUseCase**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.LearnedSignatureRepository
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class ClearLearnedSignatureUseCaseTest {

    private val repo = mockk<LearnedSignatureRepository>(relaxed = true)
    private val useCase = ClearLearnedSignatureUseCase(repo)

    @Test
    fun `calls repository clear exactly once`() {
        useCase()
        verify(exactly = 1) { repo.clear() }
    }

    @Test
    fun `does not call save or load`() {
        useCase()
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { repo.load() }
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest \
  --tests "com.wearaware.app.domain.usecase.SaveLearnedSignatureUseCaseTest" \
  --tests "com.wearaware.app.domain.usecase.ClearLearnedSignatureUseCaseTest" \
  2>&1 | tail -20
```

Expected: compilation errors — classes don't exist yet.

- [ ] **Step 4: Create SaveLearnedSignatureUseCase.kt**

```kotlin
package com.wearaware.app.domain.usecase

import android.util.Log
import com.wearaware.app.domain.model.CapturedDevice
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject

class SaveLearnedSignatureUseCase @Inject constructor(
    private val repository: LearnedSignatureRepository
) {
    operator fun invoke(device: CapturedDevice) {
        val prefixes = extractPrefixesFromSummary(device.manufacturerDataSummary)
        val signature = LearnedDeviceSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = device.fingerprintId,
            manufacturerIds = device.manufacturerIds,
            manufacturerDataPrefixes = prefixes,
            serviceUuids = device.serviceUuids
        )
        repository.save(signature)
        Log.d(
            "LearnedSignature",
            "Saved: fingerprintId=${device.fingerprintId}, " +
                "manufacturerIds=${device.manufacturerIds}, " +
                "prefixes=$prefixes, " +
                "serviceUuids=${device.serviceUuids}"
        )
    }
}
```

- [ ] **Step 5: Create ClearLearnedSignatureUseCase.kt**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject

class ClearLearnedSignatureUseCase @Inject constructor(
    private val repository: LearnedSignatureRepository
) {
    operator fun invoke() = repository.clear()
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest \
  --tests "com.wearaware.app.domain.usecase.SaveLearnedSignatureUseCaseTest" \
  --tests "com.wearaware.app.domain.usecase.ClearLearnedSignatureUseCaseTest" \
  2>&1 | tail -20
```

Expected: All 9 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCase.kt \
        app/src/main/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCase.kt \
        app/src/test/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCaseTest.kt \
        app/src/test/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCaseTest.kt
git commit -m "feat: SaveLearnedSignatureUseCase and ClearLearnedSignatureUseCase"
```

---

### Task 6: Update CaptureUiState and ScanUiState

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`

- [ ] **Step 1: Add learned fields to CaptureUiState.kt**

Add these imports at the top of the file:

```kotlin
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.model.LearnedMatchResult
```

Add these three fields to the `CaptureUiState` data class, after `isBleAvailable`:

```kotlin
    val learnedSignature: LearnedDeviceSignature? = null,
    /** Keyed by fingerprintId. Populated after compare + learned matching runs. */
    val learnedMatchResults: Map<String, LearnedMatchResult> = emptyMap(),
    /** Set by learnDevice(), shown as a snackbar, auto-cleared after 3s. */
    val learnSaveConfirmation: String? = null,
```

- [ ] **Step 2: Add learned fields to ScanUiState.kt**

Add these imports at the top of the file:

```kotlin
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.model.LearnedMatchResult
```

Add these two fields to the `ScanUiState` data class, after `activeFilter`:

```kotlin
    val learnedSignature: LearnedDeviceSignature? = null,
    /** Keyed by ObservedDevice.id. Updated on every device list tick. */
    val learnedMatchResults: Map<String, LearnedMatchResult> = emptyMap(),
```

- [ ] **Step 3: Run all existing tests to confirm no regressions**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS (new nullable fields with defaults don't break existing tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt
git commit -m "feat: add learned-signature fields to CaptureUiState and ScanUiState"
```

---

### Task 7: CaptureViewModel — learnDevice, clearLearnedDevice, matching

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt`

- [ ] **Step 1: Add new constructor parameters and init loading**

In `CaptureViewModel`, add three new injected dependencies and the repository for direct loading. The new constructor becomes:

```kotlin
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val captureRepository: CaptureRepository,
    private val matchTargetDevice: MatchTargetDeviceUseCase,
    private val compareCapturesUseCase: CompareCapturesUseCase,
    private val saveLearnedSignature: SaveLearnedSignatureUseCase,
    private val matchLearnedSignature: MatchLearnedSignatureUseCase,
    private val clearLearnedSignature: ClearLearnedSignatureUseCase,
    private val learnedSignatureRepository: LearnedSignatureRepository
) : ViewModel() {
```

Add these imports at the top of `CaptureViewModel.kt`:

```kotlin
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.ClearLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.SaveLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.extractPrefixesFromSummary
import kotlinx.coroutines.delay
```

Replace the existing `viewModelScope.launch` block inside `init` with this version that loads the signature **before** calling `runCompare`, so learned matching is available on the first compare run:

```kotlin
        viewModelScope.launch {
            val baseline = captureRepository.getSession(CaptureType.BASELINE)
            val target = captureRepository.getSession(CaptureType.TARGET)
            // Load learned signature first — runCompare reads it from state
            val sig = learnedSignatureRepository.load()
            _uiState.update {
                it.copy(
                    baseline = baseline,
                    baselineCaptureState = if (baseline != null) CaptureState.DONE else CaptureState.IDLE,
                    target = target,
                    targetCaptureState = if (target != null) CaptureState.DONE else CaptureState.IDLE,
                    learnedSignature = sig
                )
            }
            if (target != null) runCompare(baseline, target)
        }
```

This replaces the existing block that starts `viewModelScope.launch {` and ends with `if (target != null) runCompare(baseline, target)`. The rest of `init` (BLE scanning setup, device observation) is unchanged.

- [ ] **Step 2: Add learnDevice() function**

Add this function to `CaptureViewModel`, after `stopCapture()`:

```kotlin
    /**
     * Saves the device with the given fingerprintId as the learned glasses signature.
     * Device must exist in the current target session.
     * Triggers a 3-second confirmation message via learnSaveConfirmation.
     */
    fun learnDevice(fingerprintId: String) {
        val device = _uiState.value.target?.devices
            ?.firstOrNull { it.fingerprintId == fingerprintId } ?: return
        saveLearnedSignature(device)
        val sig = learnedSignatureRepository.load() ?: return
        val matchResults = computeLearnedMatchesForCompare(sig, _uiState.value.compareResults)
        _uiState.update {
            it.copy(
                learnedSignature = sig,
                learnedMatchResults = matchResults,
                learnSaveConfirmation = "Saved as My Meta Glasses"
            )
        }
        viewModelScope.launch {
            delay(3_000)
            _uiState.update { it.copy(learnSaveConfirmation = null) }
        }
    }
```

- [ ] **Step 3: Add clearLearnedDevice() function**

Add after `learnDevice()`:

```kotlin
    fun clearLearnedDevice() {
        clearLearnedSignature()
        _uiState.update {
            it.copy(
                learnedSignature = null,
                learnedMatchResults = emptyMap()
            )
        }
    }
```

- [ ] **Step 4: Update runCompare() to re-run learned matching after compare**

Replace the existing `runCompare` private function with:

```kotlin
    private suspend fun runCompare(baseline: CaptureSession?, target: CaptureSession) {
        val results = compareCapturesUseCase(baseline, target, DefaultTargetProfile.WAYFARER_00ZS)
        val sig = _uiState.value.learnedSignature
        val matchResults = if (sig != null) {
            computeLearnedMatchesForCompare(sig, results)
        } else {
            emptyMap()
        }
        _uiState.update {
            it.copy(
                compareResults = results,
                learnedMatchResults = matchResults
            )
        }
    }
```

- [ ] **Step 5: Add computeLearnedMatchesForCompare() private helper**

Add this private helper to `CaptureViewModel`, after `runCompare()`:

```kotlin
    private fun computeLearnedMatchesForCompare(
        signature: com.wearaware.app.domain.model.LearnedDeviceSignature,
        compareResults: List<com.wearaware.app.domain.model.CompareMatchResult>
    ): Map<String, com.wearaware.app.domain.model.LearnedMatchResult> {
        return compareResults.associate { result ->
            val device = result.capturedDevice
            val input = LearnedMatchInput(
                fingerprintId = device.fingerprintId,
                manufacturerIds = device.manufacturerIds,
                manufacturerDataPrefixes = extractPrefixesFromSummary(device.manufacturerDataSummary),
                serviceUuids = device.serviceUuids,
                averageRssi = device.averageRssi,
                seenCount = device.seenCount,
                visibleAtStop = device.visibleAtStop
            )
            device.fingerprintId to matchLearnedSignature(input, signature)
        }
    }
```

- [ ] **Step 6: Run all existing unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt
git commit -m "feat: CaptureViewModel — learnDevice, clearLearnedDevice, learned matching in compare"
```

---

### Task 8: ScanViewModel — reloadLearnedSignature and live matching

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`

- [ ] **Step 1: Add injected dependencies**

Add these to the `ScanViewModel` constructor:

```kotlin
    private val matchLearnedSignature: MatchLearnedSignatureUseCase,
    private val learnedSignatureRepository: LearnedSignatureRepository,
```

Add these imports at the top of `ScanViewModel.kt`:

```kotlin
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.model.LearnedMatchResult
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.extractPrefixesFromFingerprintMap
```

- [ ] **Step 2: Load signature on init**

In `ScanViewModel.init`, after the existing state update with `appVersion` etc., add:

```kotlin
        val sig = learnedSignatureRepository.load()
        if (sig != null) {
            _uiState.update { it.copy(learnedSignature = sig) }
        }
```

- [ ] **Step 3: Add reloadLearnedSignature() function**

Add this public function after `getDeviceById()`:

```kotlin
    /** Called from ScanScreen on composition to pick up signatures saved via CaptureScreen. */
    fun reloadLearnedSignature() {
        val sig = learnedSignatureRepository.load()
        _uiState.update { it.copy(learnedSignature = sig) }
    }
```

- [ ] **Step 4: Update processDeviceUpdate() to run learned matching**

In `processDeviceUpdate()`, replace the final `_uiState.update` call with this version that also computes learned matches:

```kotlin
        val learnedSig = _uiState.value.learnedSignature
        val learnedMatches: Map<String, LearnedMatchResult> = if (learnedSig != null) {
            devices.associate { device ->
                val input = LearnedMatchInput(
                    fingerprintId = device.id,
                    manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
                    manufacturerDataPrefixes = extractPrefixesFromFingerprintMap(
                        device.fingerprint?.manufacturerDataHex
                    ),
                    serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
                    averageRssi = device.averagedRssi,
                    seenCount = device.seenCount,
                    visibleAtStop = false  // live scan — visibleAtStop is a capture-only concept
                )
                device.id to matchLearnedSignature(input, learnedSig)
            }
        } else {
            emptyMap()
        }

        _uiState.update {
            it.copy(
                devices = devices,
                activeAlert = updatedAlert,
                deviceMatchScores = matchScores,
                learnedMatchResults = learnedMatches
            )
        }
```

- [ ] **Step 5: Run all unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt
git commit -m "feat: ScanViewModel — reloadLearnedSignature, live learned matching"
```

---

### Task 9: CaptureScreen UI — status banner, label override, learn button, snackbar

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt`

- [ ] **Step 1: Add SnackbarHostState and learned state to CaptureScreen**

In `CaptureScreen`, add a `SnackbarHostState` and a `LaunchedEffect` for the confirmation message. Update the `CaptureScreen` composable's `Scaffold` to include a `snackbarHost`. The full top of `CaptureScreen` (from `@Composable` through `Scaffold`) becomes:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onViewDeviceDetail: (String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show confirmation snackbar when learnSaveConfirmation is set
    LaunchedEffect(uiState.learnSaveConfirmation) {
        val msg = uiState.learnSaveConfirmation
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ... existing TopAppBar unchanged ...
        }
    ) { innerPadding ->
```

Add this import at the top of `CaptureScreen.kt`:

```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.wearaware.app.domain.model.LearnedConfidence
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.model.LearnedMatchResult
```

- [ ] **Step 2: Thread learned state into CompareResultsSection call**

Update the `CompareResultsSection` call inside the `Column` in `CaptureScreen`:

```kotlin
            if (uiState.targetCaptureState == CaptureState.DONE) {
                HorizontalDivider()
                CompareResultsSection(
                    results = uiState.compareResults,
                    hasBaseline = uiState.baseline != null,
                    onViewDevice = onViewDeviceDetail,
                    learnedSignature = uiState.learnedSignature,
                    learnedMatchResults = uiState.learnedMatchResults,
                    onLearnDevice = { viewModel.learnDevice(it) },
                    onClearLearnedDevice = { viewModel.clearLearnedDevice() }
                )
            }
```

- [ ] **Step 3: Update CompareResultsSection signature**

Replace the `CompareResultsSection` function signature and add the status banner inside it. The updated function signature and the status banner (placed after the `headerText` block and before the `CompareSummaryRow`):

```kotlin
@Composable
private fun CompareResultsSection(
    results: List<CompareMatchResult>,
    hasBaseline: Boolean,
    onViewDevice: (String) -> Unit,
    learnedSignature: LearnedDeviceSignature?,
    learnedMatchResults: Map<String, LearnedMatchResult>,
    onLearnDevice: (String) -> Unit,
    onClearLearnedDevice: () -> Unit
) {
    val safeResults = results.toList()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val headerText = if (safeResults.isEmpty()) {
            "Compare Results"
        } else {
            val count = safeResults.size
            "Compare Results — $count candidate${if (count == 1) "" else "s"}"
        }
        Text(headerText, style = MaterialTheme.typography.titleSmall)

        // Learned device status banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (learnedSignature != null)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (learnedSignature != null)
                        "Learned device profile: ${learnedSignature.displayName}"
                    else
                        "No learned glasses profile saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (learnedSignature != null)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (learnedSignature != null) {
                    TextButton(onClick = onClearLearnedDevice) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // The remainder of the function body is unchanged from the current implementation:
        // CompareSummaryRow call, all-LOW warning text, no-baseline Card, empty-results Text,
        // and the safeResults.forEachIndexed block (updated in Step 5 below).
        // Do NOT remove or restructure any of that code.
```

Also add `import androidx.compose.ui.Alignment` and `import androidx.compose.foundation.layout.Row` if not already imported.

- [ ] **Step 4: Update CompareResultCard signature and add label override + learn button**

Replace the `CompareResultCard` function signature to accept learned state:

```kotlin
@Composable
private fun CompareResultCard(
    result: CompareMatchResult,
    isTopCandidate: Boolean,
    onViewDevice: (String) -> Unit,
    learnedSignature: LearnedDeviceSignature?,
    learnedMatchResult: LearnedMatchResult?,
    onLearnDevice: (String) -> Unit
) {
```

Inside `CompareResultCard`, replace the display name section (the block starting `val displayName = device.advertisedName ...`) with this label-override version:

```kotlin
            // Label override — learned match takes priority over raw BLE label
            val learnedLabel: String? = when (learnedMatchResult?.confidence) {
                LearnedConfidence.STRONG -> learnedMatchResult.signature.displayName
                LearnedConfidence.POSSIBLE -> "Possible match to your glasses"
                else -> null
            }
            val rawLabel = device.advertisedName
                ?: device.companyNames.firstOrNull()?.let { "$it device" }
                ?: "Unknown BLE Device"

            if (learnedLabel != null) {
                Text(
                    learnedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    rawLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(rawLabel, style = MaterialTheme.typography.bodyMedium)
            }
```

At the end of `CompareResultCard`'s Column (after the "View Device Details" button), add:

```kotlin
            // Learn button — shown on top candidate only, hidden if already saved
            if (isTopCandidate) {
                val alreadySaved = learnedSignature?.fingerprintId == device.fingerprintId
                if (alreadySaved) {
                    Text(
                        "✓ Saved as learned device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onLearnDevice(device.fingerprintId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Learn this device — This is my glasses")
                    }
                }
            }
```

- [ ] **Step 5: Update CompareResultCard call sites in CompareResultsSection**

In the `safeResults.forEachIndexed` block, update the `CompareResultCard` call:

```kotlin
                    CompareResultCard(
                        result = result,
                        isTopCandidate = isTopCandidate,
                        onViewDevice = onViewDevice,
                        learnedSignature = learnedSignature,
                        learnedMatchResult = learnedMatchResults[result.capturedDevice.fingerprintId],
                        onLearnDevice = onLearnDevice
                    )
```

- [ ] **Step 6: Run all unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/CaptureScreen.kt
git commit -m "feat: CaptureScreen — learned device banner, label override, learn button, snackbar"
```

---

### Task 10: CapturedDeviceDetailScreen — learn button and debug section

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/CapturedDeviceDetailScreen.kt`

- [ ] **Step 1: Add imports**

Add at the top of `CapturedDeviceDetailScreen.kt`:

```kotlin
import com.wearaware.app.domain.model.LearnedConfidence
```

- [ ] **Step 2: Add learn button after identity section**

In the `Column` of `CapturedDeviceDetailScreen`, after the existing identity block (fingerprintId, MAC) and before the first `HorizontalDivider()`, insert:

```kotlin
            // Learn this device button
            val learnedSignature = uiState.learnedSignature
            val alreadySaved = learnedSignature?.fingerprintId == device.fingerprintId
            Button(
                onClick = { viewModel.learnDevice(device.fingerprintId) },
                enabled = !alreadySaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (alreadySaved) "Already saved as learned device"
                    else "Learn this device — This is my glasses"
                )
            }
```

- [ ] **Step 3: Add learned signature debug section**

After the existing "Compare result" section (after its `HorizontalDivider()`), add:

```kotlin
            // Learned signature match debug section
            HorizontalDivider()
            Text("Learned Signature Match", style = MaterialTheme.typography.titleSmall)
            val learnedSignatureDebug = uiState.learnedSignature
            if (learnedSignatureDebug == null) {
                Text(
                    "No learned signature saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Learned signature: ${learnedSignatureDebug.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
                val learnedMatch = uiState.learnedMatchResults[fingerprintId]
                if (learnedMatch == null) {
                    Text(
                        "Match: not computed (run compare first)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val confidenceColor = when (learnedMatch.confidence) {
                        LearnedConfidence.STRONG -> MaterialTheme.colorScheme.primary
                        LearnedConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
                        LearnedConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        "Match confidence: ${learnedMatch.confidence.name} (score ${learnedMatch.score})",
                        style = MaterialTheme.typography.bodySmall,
                        color = confidenceColor
                    )
                    Text(
                        "Label override active: ${if (learnedMatch.labelOverrideActive) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (learnedMatch.matchedSignals.isNotEmpty()) {
                        Text("Matched signals:", style = MaterialTheme.typography.labelSmall)
                        learnedMatch.matchedSignals.forEach { signal ->
                            Text(
                                "  • $signal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "No signals matched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
```

- [ ] **Step 4: Run all unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/CapturedDeviceDetailScreen.kt
git commit -m "feat: CapturedDeviceDetailScreen — learn button, learned match debug section"
```

---

### Task 11: ScanScreen status chip + DeviceCard label override

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`

- [ ] **Step 1: Add reloadLearnedSignature call to ScanScreen**

In `ScanScreen`, add a `LaunchedEffect` that calls `viewModel.reloadLearnedSignature()` each time the screen composes. Place it right after `val uiState by viewModel.uiState.collectAsState()`:

```kotlin
    // Reload learned signature each time ScanScreen becomes active — picks up signatures
    // saved via CaptureScreen (which uses a different ViewModel instance).
    LaunchedEffect(Unit) {
        viewModel.reloadLearnedSignature()
    }
```

Add this import if not already present:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

- [ ] **Step 2: Add status chip below the alert banner in ScanScreen**

In `ScanScreen`, find the `Column` inside the `Scaffold` body, after the alert banner section. Add the learned device status chip:

```kotlin
            // Learned device status chip
            if (uiState.learnedSignature != null) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            "${uiState.learnedSignature!!.displayName} profile active",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
```

- [ ] **Step 3: Update DeviceCard to accept learned match result**

Add `learnedMatchResult` as an optional parameter to `DeviceCard`:

```kotlin
@Composable
fun DeviceCard(
    device: ObservedDevice,
    onClick: () -> Unit,
    isTopCandidate: Boolean = false,
    learnedMatchResult: com.wearaware.app.domain.model.LearnedMatchResult? = null,
    modifier: Modifier = Modifier
) {
```

In `DeviceCard`, find the block that builds `displayName` (currently: `val displayName = device.advertisedName ?: device.companyNames.firstOrNull()?.let { "$it device" }...`). Replace it with this label-override version:

```kotlin
                    // Primary display label — learned match overrides raw BLE label
                    val rawLabel = device.advertisedName
                        ?: device.companyNames.firstOrNull()?.let { "$it device" }
                        ?: "Unknown (${device.classification.category.name.replace('_', ' ')}) device"
                    val learnedLabel: String? = when (learnedMatchResult?.confidence) {
                        com.wearaware.app.domain.model.LearnedConfidence.STRONG ->
                            learnedMatchResult.signature.displayName
                        com.wearaware.app.domain.model.LearnedConfidence.POSSIBLE ->
                            "Possible match to your glasses"
                        else -> null
                    }
                    if (learnedLabel != null) {
                        Text(learnedLabel, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(rawLabel, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(rawLabel, style = MaterialTheme.typography.bodyMedium)
                    }
```

- [ ] **Step 4: Pass learnedMatchResult from ScanScreen to DeviceCard**

In `ScanScreen`, find all `DeviceCard(...)` calls and add the learned match result. The call currently passes `device` and `onClick` and `isTopCandidate`. Update each to also pass:

```kotlin
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.id) },
                        isTopCandidate = ...,
                        learnedMatchResult = uiState.learnedMatchResults[device.id]
                    )
```

- [ ] **Step 5: Run all unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt \
        app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt
git commit -m "feat: ScanScreen status chip + DeviceCard learned label override"
```

---

### Task 12: CaptureViewModel integration tests

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModelLearnedTest.kt`

- [ ] **Step 1: Write the tests**

```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelLearnedTest {

    private val testDispatcher = StandardTestDispatcher()

    private val bleRepository = mockk<BleRepository>(relaxed = true)
    private val captureRepository = mockk<CaptureRepository>(relaxed = true)
    private val matchTargetDevice = mockk<MatchTargetDeviceUseCase>(relaxed = true)
    private val compareCapturesUseCase = mockk<CompareCapturesUseCase>(relaxed = true)
    private val saveLearnedSignature = mockk<SaveLearnedSignatureUseCase>(relaxed = true)
    private val matchLearnedSignature = MatchLearnedSignatureUseCase()  // real impl
    private val clearLearnedSignature = mockk<ClearLearnedSignatureUseCase>(relaxed = true)
    private val learnedSignatureRepository = mockk<LearnedSignatureRepository>(relaxed = true)

    private lateinit var viewModel: CaptureViewModel
    private val deviceFlow = MutableStateFlow<List<ObservedDevice>>(emptyList())

    private val glassesDevice = CapturedDevice(
        fingerprintId = "fp-glasses",
        advertisedName = null,
        macAddress = null,
        manufacturerIds = listOf(0x01AB),
        manufacturerDataSummary = "01ab:deadbeef01234567",
        serviceUuids = emptyList(),
        category = DeviceCategory.UNKNOWN_BLE_DEVICE,
        companyNames = emptyList(),
        firstSeenInCapture = 0L,
        lastSeenInCapture = 0L,
        peakRssi = -57,
        averageRssi = -57,
        seenCount = 128,
        visibleAtStop = true,
        targetMatchScore = null,
        targetMatchSignals = emptyList()
    )

    private val targetSession = CaptureSession(
        id = "session-TARGET",
        type = CaptureType.TARGET,
        startedAt = System.currentTimeMillis() - 30_000,
        stoppedAt = System.currentTimeMillis(),
        devices = listOf(glassesDevice)
    )

    private val savedSig = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-glasses",
        manufacturerIds = listOf(0x01AB),
        manufacturerDataPrefixes = listOf("01ab:deadbeef"),
        serviceUuids = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bleRepository.observedDevices } returns deviceFlow
        every { bleRepository.isScanning } returns false
        every { bleRepository.isBleAvailable } returns true
        // Seed the target session via captureRepository so init loads it into state
        every { captureRepository.getSession(CaptureType.TARGET) } returns targetSession
        every { captureRepository.getSession(CaptureType.BASELINE) } returns null
        every { learnedSignatureRepository.load() } returns null
        every { compareCapturesUseCase(any(), any(), any()) } returns emptyList()

        viewModel = CaptureViewModel(
            bleRepository,
            captureRepository,
            matchTargetDevice,
            compareCapturesUseCase,
            saveLearnedSignature,
            matchLearnedSignature,
            clearLearnedSignature,
            learnedSignatureRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `learnDevice saves signature and sets learnSaveConfirmation`() = runTest {
        // After learnDevice calls saveLearnedSignature, the repo returns the saved sig
        every { learnedSignatureRepository.load() } returns savedSig

        advanceUntilIdle()  // init completes: target session loaded into state

        viewModel.learnDevice("fp-glasses")
        advanceUntilIdle()

        verify { saveLearnedSignature(glassesDevice) }
        assertEquals("Saved as My Meta Glasses", viewModel.uiState.value.learnSaveConfirmation)
        assertEquals("My Meta Glasses", viewModel.uiState.value.learnedSignature?.displayName)
    }

    @Test
    fun `clearLearnedDevice clears signature and match results from state`() = runTest {
        advanceUntilIdle()
        viewModel.clearLearnedDevice()
        advanceUntilIdle()

        verify { clearLearnedSignature() }
        assertNull(viewModel.uiState.value.learnedSignature)
        assertTrue(viewModel.uiState.value.learnedMatchResults.isEmpty())
    }

    @Test
    fun `learnDevice with unknown fingerprintId is a no-op`() = runTest {
        advanceUntilIdle()
        viewModel.learnDevice("nonexistent-fp")
        advanceUntilIdle()

        verify(exactly = 0) { saveLearnedSignature(any()) }
    }

    @Test
    fun `Apple-only device is saved when explicitly tapped`() = runTest {
        val appleDevice = glassesDevice.copy(
            fingerprintId = "fp-apple",
            manufacturerIds = listOf(0x004C),
            manufacturerDataSummary = "004c:aabbccdd"
        )
        val appleSession = targetSession.copy(devices = listOf(appleDevice))
        every { captureRepository.getSession(CaptureType.TARGET) } returns appleSession
        every { learnedSignatureRepository.load() } returns savedSig.copy(fingerprintId = "fp-apple")

        // Recreate viewModel with apple session
        val vm = CaptureViewModel(
            bleRepository, captureRepository, matchTargetDevice,
            compareCapturesUseCase, saveLearnedSignature, matchLearnedSignature,
            clearLearnedSignature, learnedSignatureRepository
        )
        advanceUntilIdle()

        vm.learnDevice("fp-apple")
        advanceUntilIdle()

        // No Apple suppression for explicit user-initiated save
        verify { saveLearnedSignature(appleDevice) }
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest --tests "com.wearaware.app.ui.viewmodel.CaptureViewModelLearnedTest" 2>&1 | tail -20
```

Expected: All 3 tests PASS.

- [ ] **Step 3: Run all tests to verify no regressions**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew testDebugUnitTest 2>&1 | tail -30
```

Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModelLearnedTest.kt
git commit -m "test: CaptureViewModel — learnDevice, clearLearnedDevice, no-op on unknown id"
```
