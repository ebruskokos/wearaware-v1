# BLE Intelligence Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **SUPERSEDES:** `2026-04-04-target-device-matching.md` — this plan replaces it entirely. Do not execute that plan.

**Goal:** Upgrade WearAware with full BLE data extraction, a 50-entry manufacturer company map, hash-based device fingerprinting, enhanced classification, target device matching for "Wayfarer 00ZS", and a debug view — all within Clean Architecture rules.

**Architecture:** All Android BLE parsing remains exclusively in `data/ble/ScanResultMapper.kt`. New pure-Kotlin models (`DeviceFingerprint`, `CompanyIdMap`, `TargetMatchResult`) live in the domain layer. `BleRepositoryImpl` computes fingerprints from raw scan data and populates enriched `ObservedDevice` instances. `MatchTargetDeviceUseCase` scores devices entirely in domain. ViewModel orchestrates only.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (version bump to 3), JUnit 4 unit tests.

---

## Reality Rules (enforced throughout)

- BLE MAC addresses randomize per-session → use content-based fingerprint hash as device ID
- Device names may be absent → manufacturer ID is the most reliable brand signal
- Never claim certainty: always use "most likely", "candidate", "estimated"
- All user-facing wording still goes through `SafeWording` where applicable
- `android.bluetooth` imports stay exclusively in `BleScanner.kt` and `ScanResultMapper.kt`

---

## File Map

**New files:**
- `domain/rules/CompanyIdMap.kt` — 50+ manufacturer ID → company name mappings
- `domain/model/DeviceFingerprint.kt` — content-based fingerprint model
- `data/ble/FingerprintBuilder.kt` — extension `RawScanResult.toFingerprint()` (uses `java.security`)
- `domain/model/TargetDeviceProfile.kt` — known device profile
- `domain/model/TargetMatchResult.kt` — per-device match result (includes `MatchConfidence` enum)
- `domain/usecase/MatchTargetDeviceUseCase.kt` — scoring logic + `DefaultTargetProfile`
- `ui/components/TargetMatchBanner.kt` — best-candidate banner for ScanScreen
- `app/src/test/java/com/wearaware/app/domain/rules/CompanyIdMapTest.kt` — 3 tests
- `app/src/test/java/com/wearaware/app/data/ble/FingerprintBuilderTest.kt` — 4 tests
- `app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt` — 7 tests

**Modified files:**
- `domain/model/RawScanResult.kt` — add serviceData, advertisingFlags, isConnectable, deviceType, bondState, bluetoothDeviceName
- `domain/model/ObservedDevice.kt` — add macAddress, fingerprint, companyNames (all with defaults)
- `data/ble/ScanResultMapper.kt` — extract all new fields
- `data/repository/BleRepositoryImpl.kt` — compute fingerprint, use hash as device ID, populate new fields
- `domain/model/ScanLogEntry.kt` — add fingerprintId, manufacturerIds, targetMatchScore, targetMatchReason, isTopCandidate
- `domain/repository/ScanLogRepository.kt` — add `matchResult` param to `log()`
- `domain/usecase/LogScanEventUseCase.kt` — pass `matchResult` through
- `data/local/ScanLogEntity.kt` — add 5 new columns
- `data/local/WearAwareDatabase.kt` — bump to version 3
- `data/mapper/ScanLogMapper.kt` — map new fields
- `data/repository/ScanLogRepositoryImpl.kt` — pass `matchResult` to mapper
- `di/DatabaseModule.kt` — add fallbackToDestructiveMigration
- `ui/viewmodel/ScanUiState.kt` — add focusMode, debugMode, deviceMatchScores, computed bestMatch + sortedDevices
- `ui/viewmodel/ScanViewModel.kt` — inject MatchTargetDeviceUseCase, wire toggles
- `ui/components/DeviceCard.kt` — show company name, isTopCandidate highlight
- `ui/screens/ScanScreen.kt` — add banner + focus/debug toggles
- `ui/screens/DeviceDetailScreen.kt` — full BLE data + target analysis + debug section

---

## Task 1: CompanyIdMap

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/rules/CompanyIdMap.kt`
- Create: `app/src/test/java/com/wearaware/app/domain/rules/CompanyIdMapTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/wearaware/app/domain/rules/CompanyIdMapTest.kt
package com.wearaware.app.domain.rules

import org.junit.Assert.*
import org.junit.Test

class CompanyIdMapTest {

    @Test
    fun `nameFor returns correct name for known company ID`() {
        assertEquals("Apple", CompanyIdMap.nameFor(0x004C))
        assertEquals("Meta", CompanyIdMap.nameFor(0x0075))
        assertEquals("Google", CompanyIdMap.nameFor(0x00E0))
    }

    @Test
    fun `nameFor returns formatted unknown string for unrecognised ID`() {
        val result = CompanyIdMap.nameFor(0x9999)
        assertTrue("Expected 'Unknown' prefix, got: $result", result.startsWith("Unknown"))
        assertTrue("Expected hex in result, got: $result", result.contains("9999", ignoreCase = true))
    }

    @Test
    fun `namesFor returns list of names for multiple IDs`() {
        val names = CompanyIdMap.namesFor(listOf(0x004C, 0x0075))
        assertEquals(listOf("Apple", "Meta"), names)
    }
}
```

- [ ] **Step 2: Run to confirm FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "*.CompanyIdMapTest" 2>&1 | tail -10
```

Expected: FAIL — `CompanyIdMap not found`.

- [ ] **Step 3: Implement CompanyIdMap.kt**

```kotlin
package com.wearaware.app.domain.rules

/**
 * PURPOSE: Maps Bluetooth SIG Company Identifier codes to human-readable brand names.
 *   Used to enrich BLE scan results with manufacturer context.
 * SOURCE: Bluetooth Assigned Numbers — https://www.bluetooth.com/specifications/assigned-numbers/
 * NOTES: Not all 50,000+ registered companies are listed here.
 *   Only the ~50 most common consumer electronics brands are included for v1.
 */
object CompanyIdMap {

    val NAMES: Map<Int, String> = mapOf(
        0x004C to "Apple",
        0x0075 to "Meta",
        0x00E0 to "Google",
        0x00F7 to "Samsung",
        0x00D2 to "LG",
        0x0131 to "Beats",
        0x0059 to "Nordic Semiconductor",
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x001D to "Qualcomm",
        0x00AA to "Huawei",
        0x01A8 to "Xiaomi",
        0x00C3 to "Sony",
        0x0087 to "Bose",
        0x00FE to "Fitbit",
        0x0171 to "Garmin",
        0x018D to "Tile",
        0x00A0 to "Dell",
        0x009E to "HP",
        0x000A to "Intel",
        0x01DA to "OnePlus",
        0x01FF to "Oppo",
        0x0201 to "Vivo",
        0x0222 to "Realme",
        0x0245 to "Lenovo",
        0x026A to "Asus",
        0x0275 to "Acer",
        0x0281 to "Panasonic",
        0x02A3 to "Philips",
        0x02B7 to "JBL",
        0x02C1 to "Harman Kardon",
        0x02D3 to "Anker",
        0x02E5 to "Razer",
        0x02F7 to "Corsair",
        0x0301 to "Logitech",
        0x0312 to "GoPro",
        0x0324 to "DJI",
        0x0336 to "Tesla",
        0x0348 to "Ford",
        0x035A to "BMW",
        0x036C to "Mercedes",
        0x037E to "Audi",
        0x038F to "Toyota",
        0x0399 to "Nissan",
        0x03AB to "Hyundai",
        0x03BD to "Kia",
        0x03CF to "Oculus / Meta VR",
        0x03D8 to "Amazon",
        0x03EA to "Roku"
    )

    /** Returns the company name for the given 16-bit Bluetooth Company ID, or a formatted unknown string. */
    fun nameFor(companyId: Int): String =
        NAMES[companyId] ?: "Unknown (0x${companyId.toString(16).uppercase().padStart(4, '0')})"

    /** Returns company names for a collection of company IDs. */
    fun namesFor(ids: Collection<Int>): List<String> = ids.map { nameFor(it) }
}
```

- [ ] **Step 4: Run tests to confirm PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*.CompanyIdMapTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 3 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/rules/CompanyIdMap.kt \
        app/src/test/java/com/wearaware/app/domain/rules/CompanyIdMapTest.kt
git commit -m "feat: add CompanyIdMap with 50 manufacturer entries and 3 unit tests"
```

---

## Task 2: Expand RawScanResult and ScanResultMapper

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/RawScanResult.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/ble/ScanResultMapper.kt`

- [ ] **Step 1: Replace RawScanResult.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Pure representation of a single BLE scan result with ALL available fields.
 *   Sits at the data/domain boundary — created in data layer, consumed by domain.
 * LIMITATIONS: address may be randomized (Android 6+ BLE MAC randomization).
 *   isConnectable requires API 26+; bondState/deviceType are from BluetoothDevice.
 * NOTES: No android.bluetooth imports here — all types are Kotlin primitives or stdlib.
 */
data class RawScanResult(
    /** BLE device address. May be randomized per-session on Android 6+. */
    val address: String,
    /** Advertised device name from ScanRecord. Null if device does not broadcast name. */
    val advertisedName: String?,
    /** Device name from BluetoothDevice (bonded/cached). May differ from advertisedName. */
    val bluetoothDeviceName: String?,
    /** Raw RSSI in dBm. */
    val rssi: Int,
    /** Manufacturer-specific data. Key = Bluetooth SIG Company ID (int), value = raw bytes. */
    val manufacturerData: Map<Int, ByteArray>,
    /** Service UUIDs advertised. Normalized to lowercase strings. */
    val serviceUuids: List<String>,
    /** Service data map. Key = service UUID (lowercase), value = raw bytes. */
    val serviceData: Map<String, ByteArray>,
    /** TX power level from ad packet. Null if not present. */
    val txPowerLevel: Int?,
    /** LE advertising flags byte. Null if not present in ScanRecord. */
    val advertisingFlags: Int?,
    /** Whether the device is connectable. Requires API 26+; false if unavailable. */
    val isConnectable: Boolean,
    /** BluetoothDevice type: 0=unknown, 1=classic, 2=LE, 3=dual. */
    val deviceType: Int,
    /** BluetoothDevice bond state: 10=none, 11=bonding, 12=bonded. */
    val bondState: Int,
    /** Epoch milliseconds when this scan result was received. */
    val timestampMs: Long
)
```

- [ ] **Step 2: Replace ScanResultMapper.kt**

```kotlin
package com.wearaware.app.data.ble

import android.bluetooth.le.ScanResult
import android.os.Build
import com.wearaware.app.domain.model.RawScanResult

/**
 * PURPOSE: Maps Android's ScanResult to the domain RawScanResult.
 *   This is the ONLY place in the app that reads android.bluetooth.le.ScanResult.
 *   Extracts ALL available BLE fields for maximum identification fidelity.
 * NOTES: isConnectable() requires API 26; guarded with Build.VERSION check.
 *   serviceData keys are ParcelUuid — converted to lowercase UUID strings.
 *   deviceType and bondState are stable Android constants (int values).
 */
fun ScanResult.toRawScanResult(): RawScanResult {
    val record = scanRecord

    val manufacturerData = mutableMapOf<Int, ByteArray>()
    record?.manufacturerSpecificData?.let { sparse ->
        for (i in 0 until sparse.size()) {
            manufacturerData[sparse.keyAt(i)] = sparse.valueAt(i) ?: byteArrayOf()
        }
    }

    val serviceData = mutableMapOf<String, ByteArray>()
    record?.serviceData?.forEach { (uuid, data) ->
        serviceData[uuid.uuid.toString().lowercase()] = data ?: byteArrayOf()
    }

    val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        isConnectable
    } else {
        false
    }

    return RawScanResult(
        address = device.address,
        advertisedName = record?.deviceName ?: device.name,
        bluetoothDeviceName = device.name,
        rssi = rssi,
        manufacturerData = manufacturerData,
        serviceUuids = record?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList(),
        serviceData = serviceData,
        txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        advertisingFlags = record?.advertiseFlags?.takeIf { it >= 0 },
        isConnectable = connectable,
        deviceType = device.type,
        bondState = device.bondState,
        timestampMs = System.currentTimeMillis()
    )
}
```

- [ ] **Step 3: Run full unit tests to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/RawScanResult.kt \
        app/src/main/kotlin/com/wearaware/app/data/ble/ScanResultMapper.kt
git commit -m "feat: expand RawScanResult with full BLE fields; update ScanResultMapper"
```

---

## Task 3: DeviceFingerprint model + FingerprintBuilder + tests

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/DeviceFingerprint.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/ble/FingerprintBuilder.kt`
- Create: `app/src/test/java/com/wearaware/app/data/ble/FingerprintBuilderTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/wearaware/app/data/ble/FingerprintBuilderTest.kt
package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.RawScanResult
import org.junit.Assert.*
import org.junit.Test

class FingerprintBuilderTest {

    private fun makeRaw(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String? = null,
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
        serviceUuids: List<String> = emptyList()
    ) = RawScanResult(
        address = address,
        advertisedName = name,
        bluetoothDeviceName = null,
        rssi = -70,
        manufacturerData = manufacturerData,
        serviceUuids = serviceUuids,
        serviceData = emptyMap(),
        txPowerLevel = null,
        advertisingFlags = null,
        isConnectable = false,
        deviceType = 2,
        bondState = 10,
        timestampMs = System.currentTimeMillis()
    )

    @Test
    fun `same input produces same fingerprint ID`() {
        val raw1 = makeRaw(name = "Ray-Ban", manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        val raw2 = makeRaw(name = "Ray-Ban", manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        assertEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }

    @Test
    fun `different manufacturer data produces different fingerprint ID`() {
        val raw1 = makeRaw(manufacturerData = mapOf(0x0075 to byteArrayOf(1, 2, 3)))
        val raw2 = makeRaw(manufacturerData = mapOf(0x0075 to byteArrayOf(4, 5, 6)))
        assertNotEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }

    @Test
    fun `manufacturer names resolved via CompanyIdMap`() {
        val raw = makeRaw(manufacturerData = mapOf(0x004C to byteArrayOf(), 0x0075 to byteArrayOf()))
        val fp = raw.toFingerprint()
        assertTrue(fp.manufacturerNames.contains("Apple"))
        assertTrue(fp.manufacturerNames.contains("Meta"))
    }

    @Test
    fun `different MAC address but same content gives same fingerprint ID`() {
        val raw1 = makeRaw(address = "AA:BB:CC:DD:EE:FF", name = "TestDevice",
            manufacturerData = mapOf(0x004C to byteArrayOf(1)))
        val raw2 = makeRaw(address = "11:22:33:44:55:66", name = "TestDevice",
            manufacturerData = mapOf(0x004C to byteArrayOf(1)))
        assertEquals(raw1.toFingerprint().fingerprintId, raw2.toFingerprint().fingerprintId)
    }
}
```

- [ ] **Step 2: Run to confirm FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "*.FingerprintBuilderTest" 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Create DeviceFingerprint.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Content-based device identity derived from stable BLE advertising fields.
 *   fingerprintId is a SHA-256 hash of manufacturer data + service UUIDs + normalized name.
 *   Does NOT include MAC address — stable across MAC rotations within a session.
 * NOTES: Two physically different devices may theoretically produce the same fingerprint
 *   if they advertise identical manufacturer data, UUIDs, and name. This is extremely
 *   unlikely for real consumer devices. Documented as a known limitation.
 */
data class DeviceFingerprint(
    /** First 16 hex chars of SHA-256 hash of stable advertising fields. */
    val fingerprintId: String,
    /** Bluetooth SIG Company IDs present in manufacturer-specific data. */
    val manufacturerIds: List<Int>,
    /** Resolved company names from CompanyIdMap. */
    val manufacturerNames: List<String>,
    /** Manufacturer data as hex strings, keyed by Company ID. */
    val manufacturerDataHex: Map<Int, String>,
    /** Service UUIDs advertised by the device. */
    val serviceUuids: List<String>,
    /** Lowercase-trimmed advertised name, or null. */
    val normalizedName: String?,
    /** TX power level, or null if not present. */
    val txPower: Int?
)
```

- [ ] **Step 4: Create FingerprintBuilder.kt**

```kotlin
package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.DeviceFingerprint
import com.wearaware.app.domain.model.RawScanResult
import com.wearaware.app.domain.rules.CompanyIdMap
import java.security.MessageDigest

/**
 * PURPOSE: Derives a content-based DeviceFingerprint from a RawScanResult.
 *   fingerprintId is stable across MAC rotations — it depends only on advertising content.
 * NOTES: Uses SHA-256 from java.security (standard Java — no Android dependency).
 *   The hash input is: sorted manufacturer IDs + their hex data + sorted service UUIDs + normalized name.
 *   First 16 hex chars of the hash are used as the ID (64-bit collision space, sufficient for session use).
 */
fun RawScanResult.toFingerprint(): DeviceFingerprint {
    val stableInput = buildString {
        manufacturerData.keys.sorted().forEach { id ->
            append(id.toString(16).padStart(4, '0'))
            manufacturerData[id]?.let { append(it.toHexString()) }
        }
        serviceUuids.sorted().forEach { append(it) }
        advertisedName?.lowercase()?.trim()?.let { append(it) }
    }

    val hashBytes = MessageDigest.getInstance("SHA-256").digest(stableInput.toByteArray(Charsets.UTF_8))
    val fingerprintId = hashBytes.toHexString().take(16)

    return DeviceFingerprint(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerData.keys.sorted(),
        manufacturerNames = CompanyIdMap.namesFor(manufacturerData.keys),
        manufacturerDataHex = manufacturerData.mapValues { it.value.toHexString() },
        serviceUuids = serviceUuids,
        normalizedName = advertisedName?.lowercase()?.trim(),
        txPower = txPowerLevel
    )
}

/** Converts a ByteArray to a lowercase hex string. */
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
```

- [ ] **Step 5: Run tests to confirm PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*.FingerprintBuilderTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 4 tests passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/DeviceFingerprint.kt \
        app/src/main/kotlin/com/wearaware/app/data/ble/FingerprintBuilder.kt \
        app/src/test/java/com/wearaware/app/data/ble/FingerprintBuilderTest.kt
git commit -m "feat: add DeviceFingerprint model and FingerprintBuilder with 4 tests"
```

---

## Task 4: Expand ObservedDevice and update BleRepositoryImpl

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/ObservedDevice.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt`

- [ ] **Step 1: Replace ObservedDevice.kt**

Add `macAddress`, `fingerprint`, `companyNames` at the end with default values so existing tests are unaffected:

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Enriched domain representation of a BLE device observed during a scan session.
 *   Built by BleRepositoryImpl from raw scan data; consumed by use cases and ViewModels.
 * NOTES: id is the content-based fingerprintId (stable across MAC rotations).
 *   macAddress stores the raw BLE address for display purposes only — not used as identity.
 *   seenDurationMs is a computed property: lastSeenAt - firstSeenAt.
 */
data class ObservedDevice(
    /** Content-based fingerprint hash. Stable across BLE MAC address rotations. */
    val id: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: ProximityLabel,
    val visibilityState: VisibilityState,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val seenCount: Int,
    val classification: ClassificationResult,
    val persistenceAlert: PersistenceAlert?,
    /** Raw BLE MAC address — may be randomized per-session. For display only. */
    val macAddress: String? = null,
    /** Content-based fingerprint derived from advertising fields. */
    val fingerprint: DeviceFingerprint? = null,
    /** Resolved company/brand names from manufacturer IDs (e.g. "Meta", "Apple"). */
    val companyNames: List<String> = emptyList()
) {
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
```

- [ ] **Step 2: Update BleRepositoryImpl.kt**

Key changes:
1. `DeviceState` gets `macAddress: String` and `fingerprint: DeviceFingerprint`
2. `processRawScanResult()` computes fingerprint via `raw.toFingerprint()`, uses `fingerprintId` as map key
3. `emitDeviceList()` populates `macAddress`, `fingerprint`, `companyNames` on `ObservedDevice`
4. Remove the old `toDeviceFingerprint()` extension

Replace the full file:

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.ble.BleScanner
import com.wearaware.app.data.ble.toFingerprint
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.rules.FingerprintClassifier
import com.wearaware.app.domain.rules.ProximityConfig
import com.wearaware.app.domain.rules.RssiSmoother
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements BleRepository:
 *   1. Collects raw scan results from BleScanner
 *   2. Computes a content-based fingerprint (hash of manufacturer data + UUIDs + name)
 *   3. Aggregates per-device state keyed by fingerprintId
 *   4. Classifies via FingerprintClassifier
 *   5. Computes ProximityLabel and VisibilityState
 *   6. Expires devices after REMOVE_AFTER_MS in SIGNAL_LOST state
 *   7. Emits sorted List<ObservedDevice> via StateFlow
 *
 * CONTINUOUS PRESENCE RULE:
 *   Brief signal flickers < SIGNAL_LOST_AFTER_MS do NOT reset firstSeenAt.
 *   Device removed only after REMOVE_AFTER_MS in SIGNAL_LOST. This means
 *   seenDurationMs accumulates over brief gaps — intentional design for real-world BLE noise.
 */
@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val classifier: FingerprintClassifier
) : BleRepository {

    private data class DeviceState(
        val smoother: RssiSmoother = RssiSmoother(),
        val macAddress: String,
        val fingerprint: DeviceFingerprint,
        val firstSeenAt: Long,
        var lastSeenAt: Long,
        var seenCount: Int = 1,
        var rawRssi: Int,
        var averagedRssi: Int,
        val advertisedName: String?,
        val manufacturerData: Map<Int, ByteArray>,
        val serviceUuids: List<String>,
        val txPowerLevel: Int?
    )

    private val deviceStates = ConcurrentHashMap<String, DeviceState>()
    private val _observedDevices = MutableStateFlow<List<ObservedDevice>>(emptyList())
    override val observedDevices: StateFlow<List<ObservedDevice>> = _observedDevices.asStateFlow()
    override val isBleAvailable: Boolean get() = bleScanner.isBleAvailable

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var expiryJob: Job? = null

    override fun startScanning() {
        if (bleScanner.isScanning) return
        bleScanner.startScanning()

        scanJob = repositoryScope.launch {
            bleScanner.results.collect { raw ->
                processRawScanResult(raw)
                emitDeviceList()
            }
        }

        expiryJob = repositoryScope.launch {
            while (isActive) {
                delay(2_000L)
                removeExpiredDevices()
                emitDeviceList()
            }
        }
    }

    override fun stopScanning() {
        bleScanner.stopScanning()
        scanJob?.cancel()
        expiryJob?.cancel()
        scanJob = null
        expiryJob = null
        deviceStates.clear()
        _observedDevices.value = emptyList()
    }

    private fun processRawScanResult(raw: RawScanResult) {
        val fp = raw.toFingerprint()
        val key = fp.fingerprintId
        val now = System.currentTimeMillis()
        val existing = deviceStates[key]

        if (existing != null) {
            existing.lastSeenAt = now
            existing.seenCount++
            existing.rawRssi = raw.rssi
            existing.averagedRssi = existing.smoother.addReading(raw.rssi)
        } else {
            val smoother = RssiSmoother()
            deviceStates[key] = DeviceState(
                smoother = smoother,
                macAddress = raw.address,
                fingerprint = fp,
                firstSeenAt = now,
                lastSeenAt = now,
                rawRssi = raw.rssi,
                averagedRssi = smoother.addReading(raw.rssi),
                advertisedName = raw.advertisedName,
                manufacturerData = raw.manufacturerData,
                serviceUuids = raw.serviceUuids,
                txPowerLevel = raw.txPowerLevel
            )
        }
    }

    private fun removeExpiredDevices() {
        val now = System.currentTimeMillis()
        deviceStates.entries.removeIf { (_, state) ->
            now - state.lastSeenAt > ProximityConfig.REMOVE_AFTER_MS
        }
    }

    private fun emitDeviceList() {
        val now = System.currentTimeMillis()
        val devices = deviceStates.entries
            .map { (fingerprintId, state) ->
                val visibilityState =
                    if (now - state.lastSeenAt > ProximityConfig.SIGNAL_LOST_AFTER_MS)
                        VisibilityState.SIGNAL_LOST
                    else
                        VisibilityState.DETECTED_NOW

                val rawScanForClassification = RawScanResult(
                    address = state.macAddress,
                    advertisedName = state.advertisedName,
                    bluetoothDeviceName = null,
                    rssi = state.rawRssi,
                    manufacturerData = state.manufacturerData,
                    serviceUuids = state.serviceUuids,
                    serviceData = emptyMap(),
                    txPowerLevel = state.txPowerLevel,
                    advertisingFlags = null,
                    isConnectable = false,
                    deviceType = 0,
                    bondState = 10,
                    timestampMs = state.lastSeenAt
                )
                val classification = classifier.classify(rawScanForClassification)

                ObservedDevice(
                    id = fingerprintId,
                    advertisedName = state.advertisedName,
                    rawRssi = state.rawRssi,
                    averagedRssi = state.averagedRssi,
                    proximityLabel = ProximityConfig.labelFromRssi(state.averagedRssi),
                    visibilityState = visibilityState,
                    firstSeenAt = state.firstSeenAt,
                    lastSeenAt = state.lastSeenAt,
                    seenCount = state.seenCount,
                    classification = classification,
                    persistenceAlert = null,
                    macAddress = state.macAddress,
                    fingerprint = state.fingerprint,
                    companyNames = state.fingerprint.manufacturerNames
                )
            }
            .sortedByDescending { it.averagedRssi }

        _observedDevices.value = devices
    }
}
```

- [ ] **Step 3: Run full unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/ObservedDevice.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt
git commit -m "feat: add macAddress/fingerprint/companyNames to ObservedDevice; use hash fingerprint as device ID in BleRepositoryImpl"
```

---

## Task 5: TargetDeviceProfile, TargetMatchResult, MatchTargetDeviceUseCase + tests

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/TargetDeviceProfile.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/TargetMatchResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCase.kt`
- Create: `app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt
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
```

- [ ] **Step 2: Run to confirm FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "*.MatchTargetDeviceUseCaseTest" 2>&1 | tail -10
```

- [ ] **Step 3: Create TargetDeviceProfile.kt**

```kotlin
package com.wearaware.app.domain.model

data class TargetDeviceProfile(
    val friendlyName: String,
    val modelHint: String,
    val brandHint: String,
    val category: DeviceCategory
)
```

- [ ] **Step 4: Create TargetMatchResult.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * Scoring result for one ObservedDevice against a TargetDeviceProfile.
 * isTopCandidate is set by MatchTargetDeviceUseCase after comparing all devices.
 */
data class TargetMatchResult(
    val deviceId: String,
    val score: Int,
    val confidence: MatchConfidence,
    val matchedSignals: List<String>,
    val isTopCandidate: Boolean
)

enum class MatchConfidence {
    HIGH,    // score >= 9
    MEDIUM,  // score >= 6
    LOW,     // score >= 3
    NONE     // score < 3
}
```

- [ ] **Step 5: Create MatchTargetDeviceUseCase.kt**

```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

object DefaultTargetProfile {
    val WAYFARER_00ZS = TargetDeviceProfile(
        friendlyName = "Wayfarer 00ZS",
        modelHint = "Wayfarer",
        brandHint = "Meta",
        category = DeviceCategory.SMART_GLASSES
    )
}

/**
 * PURPOSE: Scores each ObservedDevice against a TargetDeviceProfile.
 *
 * Scoring (additive):
 *   +6  exact advertised name match ("Wayfarer 00ZS")
 *   +4  Bluetooth device name match (cached/bonded)
 *   +3  partial name match containing modelHint ("Wayfarer")
 *   +3  Meta manufacturer match (matchedRuleId contains "meta" or "rayban")
 *   +3  SMART_GLASSES classification
 *   +2  CAMERA_CAPABLE_WEARABLE classification
 *   +2  service UUID pattern match (any UUID in serviceUuids)
 *   +1  RSSI bonus (NEARBY or closer)
 *   +1  persistence bonus (>= 30 seconds)
 *
 * Confidence: HIGH >= 9 | MEDIUM >= 6 | LOW >= 3 | NONE < 3
 * isTopCandidate: set for single highest-scoring device with confidence != NONE.
 *
 * NOTES: Never claims certainty. "isTopCandidate" means "best available candidate",
 *   not "confirmed match". BLE data is incomplete by nature.
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

        // Exact advertised name match (+6)
        if (device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) == true) {
            score += 6
            signals += "Exact name match: \"${device.advertisedName}\""
        } else if (device.advertisedName?.contains(profile.modelHint, ignoreCase = true) == true) {
            // Partial name match — modelHint (+3)
            score += 3
            signals += "Partial name match (model): \"${device.advertisedName}\""
        }

        // Fingerprint normalizedName (covers bluetoothDeviceName via FingerprintBuilder)
        val normalizedFpName = device.fingerprint?.normalizedName
        if (normalizedFpName != null &&
            device.advertisedName?.equals(profile.friendlyName, ignoreCase = true) != true) {
            when {
                normalizedFpName.equals(profile.friendlyName, ignoreCase = true) -> {
                    score += 4
                    signals += "Bluetooth device name match: \"$normalizedFpName\""
                }
                normalizedFpName.contains(profile.modelHint, ignoreCase = true) -> {
                    score += 2
                    signals += "Bluetooth device name partial match: \"$normalizedFpName\""
                }
            }
        }

        // SMART_GLASSES classification (+3)
        if (device.classification.category == DeviceCategory.SMART_GLASSES) {
            score += 3
            signals += "Classification: SMART_GLASSES"
        } else if (device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE) {
            // CAMERA_CAPABLE_WEARABLE (+2)
            score += 2
            signals += "Classification: CAMERA_CAPABLE_WEARABLE"
        }

        // Meta manufacturer match (+3)
        val ruleId = device.classification.matchedRuleId?.lowercase() ?: ""
        if (ruleId.contains("meta") || ruleId.contains("rayban")) {
            score += 3
            signals += "Manufacturer match: Meta/Ray-Ban rule (${device.classification.matchedRuleId})"
        }

        // Service UUID pattern match (+2) — any UUID present is a signal
        if (device.fingerprint?.serviceUuids?.isNotEmpty() == true) {
            score += 2
            signals += "Service UUIDs present: ${device.fingerprint.serviceUuids.size} UUID(s)"
        }

        // RSSI bonus (+1 if NEARBY or closer)
        if (device.proximityLabel == ProximityLabel.NEARBY ||
            device.proximityLabel == ProximityLabel.STRONG ||
            device.proximityLabel == ProximityLabel.VERY_CLOSE) {
            score += 1
            signals += "Signal: ${device.proximityLabel.name} (${device.averagedRssi} dBm)"
        }

        // Persistence bonus (+1 if present >= 30s)
        if (device.seenDurationMs >= 30_000L) {
            score += 1
            signals += "Persistent: present for ${device.seenDurationMs / 1000}s"
        }

        val confidence = when {
            score >= 9 -> MatchConfidence.HIGH
            score >= 6 -> MatchConfidence.MEDIUM
            score >= 3 -> MatchConfidence.LOW
            else       -> MatchConfidence.NONE
        }

        return TargetMatchResult(
            deviceId = device.id,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            isTopCandidate = false
        )
    }
}
```

- [ ] **Step 6: Run tests to confirm PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*.MatchTargetDeviceUseCaseTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 7 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/TargetDeviceProfile.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/TargetMatchResult.kt \
        app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCase.kt \
        app/src/test/java/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt
git commit -m "feat: add TargetDeviceProfile, TargetMatchResult, MatchTargetDeviceUseCase with 7 unit tests"
```

---

## Task 6: Update ScanUiState and ScanViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`

- [ ] **Step 1: Replace ScanUiState.kt**

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
    val debugMode: Boolean = false,
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap()
) {
    /** Best-scoring candidate device paired with its match result, or null. */
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull { it.isTopCandidate } ?: return null
            val device = devices.find { it.id == top.deviceId } ?: return null
            return device to top
        }

    /**
     * In focus mode: sorted by target match score descending.
     * Otherwise: sorted by signal strength (from BleRepositoryImpl).
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

- [ ] **Step 2: Replace ScanViewModel.kt**

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
            observeScannedDevices().collect { devices -> processDeviceUpdate(devices) }
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

    fun toggleDebugMode() {
        _uiState.update { it.copy(debugMode = !it.debugMode) }
    }

    fun getDeviceById(deviceId: String): ObservedDevice? =
        _uiState.value.devices.find { it.id == deviceId }

    private fun processDeviceUpdate(devices: List<ObservedDevice>) {
        // 1. Compute target match scores
        val matchScores = matchTargetDevice(devices, DefaultTargetProfile.WAYFARER_00ZS)

        // 2. Log new devices with match score
        devices
            .filter { it.id !in loggedDeviceIds && it.visibilityState == VisibilityState.DETECTED_NOW }
            .forEach { device ->
                loggedDeviceIds.add(device.id)
                viewModelScope.launch { logScanEvent(device, matchScores[device.id]) }
            }

        // 3. Evaluate persistence alerts
        val newAlerts = devices.mapNotNull { device ->
            evaluatePersistence(device, lastAlertedAt)?.also { alert ->
                lastAlertedAt["${device.id}:${alert.alertType.name}"] = alert.triggeredAt
            }
        }

        val currentAlert = _uiState.value.activeAlert
        val updatedAlert: PersistenceAlert? = when {
            currentAlert != null && deviceIsSignalLost(devices, currentAlert.deviceId) -> null
            newAlerts.isNotEmpty() -> newAlerts.maxByOrNull { it.triggeredAt }
            else -> currentAlert
        }

        _uiState.update {
            it.copy(devices = devices, activeAlert = updatedAlert, deviceMatchScores = matchScores)
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

- [ ] **Step 3: Run full unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt
git commit -m "feat: add focusMode, debugMode, deviceMatchScores to ScanUiState; wire MatchTargetDeviceUseCase in ScanViewModel"
```

---

## Task 7: TargetMatchBanner component + update DeviceCard

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/components/TargetMatchBanner.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`

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
                val displayName = device.advertisedName
                    ?: device.companyNames.firstOrNull()?.let { "$it device" }
                    ?: device.classification.displayLabel.ifBlank { null }
                    ?: "BLE Device"

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (device.companyNames.isNotEmpty()) {
                    Text(
                        text = "Manufacturer: ${device.companyNames.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "MAC: ${device.macAddress ?: device.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${device.classification.displayLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Confidence: ${match.confidence.name} • Score: ${match.score} • ${device.averagedRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (match.matchedSignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Why this device:",
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

- [ ] **Step 2: Replace DeviceCard.kt**

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
                    // Primary: advertised name, company name, or classification label
                    val displayName = device.advertisedName
                        ?: device.companyNames.firstOrNull()?.let { "$it device" }
                        ?: device.classification.displayLabel.ifBlank { null }
                        ?: "BLE Device"
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    // Manufacturer if known
                    if (device.companyNames.isNotEmpty()) {
                        Text(
                            text = device.companyNames.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/TargetMatchBanner.kt \
        app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt
git commit -m "feat: add TargetMatchBanner; update DeviceCard with company name and top-candidate highlight"
```

---

## Task 8: Update ScanScreen

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`

- [ ] **Step 1: Replace ScanScreen.kt**

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
                        TextButton(onClick = { viewModel.toggleDebugMode() }) {
                            Text(
                                text = if (uiState.debugMode) "Hide Debug" else "Debug",
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
            // Persistence alert banner
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
                        }) { Text("Turn on Bluetooth") }
                    }
                }
            }

            // Target match banner (while scanning)
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
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                    items(items = uiState.sortedDevices, key = { it.id }) { device ->
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
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(if (uiState.scanState == ScanState.SCANNING) "Stop Scan" else "Start Scan")
            }

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

- [ ] **Step 2: Run full unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt
git commit -m "feat: add TargetMatchBanner, focus mode, debug mode toggle to ScanScreen"
```

---

## Task 9: Update DeviceDetailScreen with full BLE data, target analysis, and debug view

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`

- [ ] **Step 1: Read current DeviceDetailScreen.kt to understand existing sections**

Read: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`

- [ ] **Step 2: Replace DeviceDetailScreen.kt**

Replace the full file with a version that adds:
- Company/manufacturer section
- Full fingerprint section
- Target match analysis section
- Debug section (shown when `uiState.debugMode == true`)

```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.DeviceCategory
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.ui.components.SafeWording
import com.wearaware.app.ui.components.SignalBars
import com.wearaware.app.ui.components.VisibilityBadge
import com.wearaware.app.ui.viewmodel.ScanViewModel
import com.wearaware.app.util.formatDuration
import com.wearaware.app.util.formatFullTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val device: ObservedDevice? = uiState.devices.firstOrNull { it.id == deviceId }
    val matchResult = uiState.deviceMatchScores[deviceId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.advertisedName ?: "Device Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (device == null) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Device no longer in session.") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Identity ---
            Text(
                text = device.advertisedName ?: "No advertised name",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "MAC: ${device.macAddress ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Fingerprint ID: ${device.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // --- Manufacturer / Company ---
            Text("Manufacturer", style = MaterialTheme.typography.titleSmall)
            if (device.companyNames.isNotEmpty()) {
                device.companyNames.forEach { name ->
                    Text(text = "• $name", style = MaterialTheme.typography.bodySmall)
                }
                device.fingerprint?.manufacturerIds?.forEach { id ->
                    Text(
                        text = "  Company ID: 0x${id.toString(16).uppercase().padStart(4, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "No manufacturer data in advertisement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // --- Signal + Visibility ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(proximity = device.proximityLabel)
                Spacer(modifier = Modifier.width(8.dp))
                VisibilityBadge(state = device.visibilityState)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = device.proximityLabel.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "Averaged RSSI: ${device.averagedRssi} dBm  •  Raw: ${device.rawRssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            // --- Classification ---
            Text("Classification", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (device.classification.category == DeviceCategory.UNKNOWN_BLE_DEVICE)
                    SafeWording.UNKNOWN_DEVICE
                else
                    device.classification.displayLabel,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Category: ${device.classification.category}", style = MaterialTheme.typography.bodySmall)
            Text("Confidence: ${device.classification.confidence}", style = MaterialTheme.typography.bodySmall)
            device.classification.evaluationNotes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
            device.classification.matchedRuleId?.let {
                Text("Rule: $it (v${device.classification.ruleVersion})", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            // --- Session Info ---
            Text("Session Info", style = MaterialTheme.typography.titleSmall)
            Text("First seen: ${device.firstSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
            Text("Last seen: ${device.lastSeenAt.formatFullTimestamp()}", style = MaterialTheme.typography.bodySmall)
            Text("Duration nearby: ${device.seenDurationMs.formatDuration()}", style = MaterialTheme.typography.bodySmall)
            Text("Scan count: ${device.seenCount}", style = MaterialTheme.typography.bodySmall)

            // Persistence alert history
            device.persistenceAlert?.let { alert ->
                HorizontalDivider()
                Text("Alert History", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Alert at ${alert.triggeredAt.formatFullTimestamp()}: ${SafeWording.DEVICE_REMAINED}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // --- Target Match Analysis ---
            Text("Target Match Analysis", style = MaterialTheme.typography.titleSmall)
            Text("Target: Wayfarer 00ZS", style = MaterialTheme.typography.bodySmall)
            if (matchResult == null || matchResult.score == 0) {
                Text(
                    text = "No match signals found for this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Score: ${matchResult.score} • Confidence: ${matchResult.confidence.name}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (matchResult.isTopCandidate) {
                    Text(
                        text = "★ Currently the best candidate for Wayfarer 00ZS",
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

            // --- Debug View (shown when debug mode is on) ---
            if (uiState.debugMode) {
                HorizontalDivider()
                Text("Debug Info", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = "Fingerprint ID: ${device.id}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "MAC address: ${device.macAddress ?: "n/a"}",
                    style = MaterialTheme.typography.labelSmall
                )
                val fp = device.fingerprint
                if (fp != null) {
                    Text(
                        text = "Manufacturer IDs: ${fp.manufacturerIds.map { "0x${it.toString(16).uppercase()}" }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    fp.manufacturerDataHex.forEach { (id, hex) ->
                        Text(
                            text = "  0x${id.toString(16).uppercase()}: $hex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (fp.serviceUuids.isNotEmpty()) {
                        Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                        fp.serviceUuids.forEach { uuid ->
                            Text(
                                text = "  $uuid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    fp.txPower?.let {
                        Text("TX Power: $it dBm", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = "Classification rule: ${device.classification.matchedRuleId ?: "none"}",
                    style = MaterialTheme.typography.labelSmall
                )
                matchResult?.let {
                    Text(
                        text = "Target match score: ${it.score} (${it.confidence.name})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            HorizontalDivider()

            // --- Disclaimers ---
            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = SafeWording.RECORDING_UNKNOWN,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 3: Run full unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt
git commit -m "feat: add manufacturer section, fingerprint, target analysis, and debug view to DeviceDetailScreen"
```

---

## Task 10: Update logging chain

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/repository/ScanLogRepository.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/usecase/LogScanEventUseCase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`

- [ ] **Step 1: Replace ScanLogEntry.kt**

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
    val fingerprintId: String? = null,
    val manufacturerIds: String? = null,     // stored as comma-separated hex (e.g. "0075,004C")
    val targetMatchScore: Int? = null,
    val targetMatchReason: String? = null,
    val isTopCandidate: Boolean = false
)
```

- [ ] **Step 2: Replace ScanLogRepository.kt**

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

- [ ] **Step 3: Replace LogScanEventUseCase.kt**

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

- [ ] **Step 4: Replace ScanLogEntity.kt**

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
    @ColumnInfo(defaultValue = "NULL") val fingerprintId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val manufacturerIds: String? = null,
    @ColumnInfo(defaultValue = "NULL") val targetMatchScore: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val targetMatchReason: String? = null,
    @ColumnInfo(defaultValue = "0") val isTopCandidate: Boolean = false
)
```

- [ ] **Step 5: Replace WearAwareDatabase.kt**

```kotlin
package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate (plan superseded)
 *   3 → added fingerprintId, manufacturerIds (BLE intelligence upgrade)
 * NOTES: fallbackToDestructiveMigration used — session logs are ephemeral and user-clearable.
 */
@Database(entities = [ScanLogEntity::class], version = 3, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 6: Update DatabaseModule.kt** — add fallbackToDestructiveMigration

Change the `provideDatabase` function body to:

```kotlin
Room.databaseBuilder(
    context,
    WearAwareDatabase::class.java,
    WearAwareDatabase.DATABASE_NAME
)
    .fallbackToDestructiveMigration()
    .build()
```

- [ ] **Step 7: Replace ScanLogMapper.kt**

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
    fingerprintId = fingerprint?.fingerprintId,
    manufacturerIds = fingerprint?.manufacturerIds
        ?.joinToString(",") { it.toString(16).padStart(4, '0') },
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
    fingerprintId = fingerprintId,
    manufacturerIds = manufacturerIds,
    targetMatchScore = targetMatchScore,
    targetMatchReason = targetMatchReason,
    isTopCandidate = isTopCandidate
)
```

- [ ] **Step 8: Replace ScanLogRepositoryImpl.kt**

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.local.ScanLogDao
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.data.mapper.toScanLogEntity
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

Expected: BUILD SUCCESSFUL. Should show 55+ tests passing.

- [ ] **Step 10: Commit logging chain**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt \
        app/src/main/kotlin/com/wearaware/app/domain/repository/ScanLogRepository.kt \
        app/src/main/kotlin/com/wearaware/app/domain/usecase/LogScanEventUseCase.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt \
        app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/ScanLogRepositoryImpl.kt \
        app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt
git commit -m "feat: update logging chain — fingerprintId, manufacturerIds, targetMatchScore in scan log (DB v3)"
```

---

## Task 11: Final build verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, 55+ tests:
- CompanyIdMapTest: 3
- FingerprintBuilderTest: 4
- MatchTargetDeviceUseCaseTest: 7
- ProximityConfigTest: 8
- RssiSmootherTest: 8
- ObservedDeviceTest: 3
- FingerprintClassifierTest: 11
- ScanLogMapperTest: 4
- EvaluatePersistenceUseCaseTest: 14

- [ ] **Step 2: Install on Poco X3**

```bash
./gradlew installDebug
```

- [ ] **Step 3: Manual verification**

With Meta glasses powered on:
1. Open WearAware → Start Scan → grant permissions
2. Check TargetMatchBanner shows at top
3. If Meta glasses BLE is active: confirm manufacturer shows "Meta" on device card
4. Tap a device → verify Manufacturer section, Fingerprint ID, Target Match Analysis
5. Tap "Debug" in toolbar → confirm raw hex, service UUIDs, fingerprint visible
6. Tap "Focus Mode" → list re-sorts by match score

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: BLE intelligence upgrade complete — 55+ tests, full manufacturer detection, target matching, debug view"
```
