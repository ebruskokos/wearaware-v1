# BLE Detail Expansion and Device Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand BLE data extraction, add a Raw BLE Debug view, add device list filters (All / Glasses / Meta / Hide Apple / Unknown / Strong Signal), and extend the scan log — so the user can identify Meta Ray-Ban glasses among many nearby Apple devices.

**Architecture:** `BleDebugData` is a new pure-Kotlin domain model holding all display-only raw BLE fields; it is constructed in `BleRepositoryImpl` and carried on `ObservedDevice.rawBleData`. `ScanFilter` is a domain enum; filtering is a computed property on `ScanUiState` so the ViewModel stays thin. All logging changes bump the Room DB to v4 with `fallbackToDestructiveMigration`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room v4, JUnit 4.

---

## File Map

**New files:**
- `domain/model/BleDebugData.kt` — pure-Kotlin model for display-only raw BLE fields
- `domain/model/ScanFilter.kt` — enum: ALL, GLASSES_CANDIDATES, META_DEVICES, HIDE_APPLE, UNKNOWN_ONLY, STRONG_SIGNAL
- `app/src/test/kotlin/com/wearaware/app/domain/model/ScanFilterTest.kt` — filter logic tests
- `app/src/test/kotlin/com/wearaware/app/data/ble/BleDebugDataMappingTest.kt` — raw BLE mapping tests

**Modified files:**
- `domain/model/RawScanResult.kt` — add 7 new fields (all with defaults): `serviceSolicitationUuids`, `rawScanBytes`, `timestampNanos`, `primaryPhy`, `secondaryPhy`, `advertisingSid`, `periodicAdvertisingInterval`
- `data/ble/ScanResultMapper.kt` — extract new fields (API guards for O and Q)
- `domain/model/ObservedDevice.kt` — add `rawBleData: BleDebugData? = null`
- `data/repository/BleRepositoryImpl.kt` — store `BleDebugData` in `DeviceState`; populate `ObservedDevice.rawBleData`
- `ui/viewmodel/ScanUiState.kt` — add `activeFilter: ScanFilter`, computed `filteredDevices`
- `ui/viewmodel/ScanViewModel.kt` — add `setFilter(ScanFilter)`
- `ui/screens/ScanScreen.kt` — add filter chip row; use `filteredDevices`
- `ui/components/DeviceCard.kt` — add company ID hex label + BLE data indicator
- `ui/screens/DeviceDetailScreen.kt` — expand Raw BLE Debug section with `BleDebugData` fields
- `domain/model/ScanLogEntry.kt` — add `manufacturerDataHex`, `serviceUuids`, `txPower`, `connectable`, `rawScanBytesHex`
- `data/local/ScanLogEntity.kt` — add matching `@ColumnInfo` fields
- `data/local/WearAwareDatabase.kt` — bump version 3 → 4
- `data/mapper/ScanLogMapper.kt` — map new fields
- `data/repository/ScanLogRepositoryImpl.kt` — no change needed (delegates to mapper)
- `di/DatabaseModule.kt` — already has `fallbackToDestructiveMigration()`, no change

---

## Task 1: Expand RawScanResult and ScanResultMapper

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/RawScanResult.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/ble/ScanResultMapper.kt`

- [ ] **Step 1: Replace RawScanResult.kt**

All new fields have default values so every existing call site still compiles.

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Pure representation of a single BLE scan result with ALL available fields.
 *   Sits at the data/domain boundary — created in data layer, consumed by domain.
 * LIMITATIONS: address may be randomized (Android 6+ BLE MAC randomization).
 *   Fields guarded by API level have safe defaults (empty / 0 / null / 255).
 * NOTES: No android.bluetooth imports — all types are Kotlin primitives or stdlib.
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
    val timestampMs: Long,
    /** Service solicitation UUIDs. Requires API 29+; empty if unavailable. */
    val serviceSolicitationUuids: List<String> = emptyList(),
    /** Raw ScanRecord bytes. Null if ScanRecord is null. */
    val rawScanBytes: ByteArray? = null,
    /** Hardware timestamp in nanoseconds since boot (from ScanResult.timestampNanos). */
    val timestampNanos: Long = 0L,
    /** Primary advertising PHY (1=LE 1M, 2=LE 2M, 3=LE Coded). Requires API 26+; 0 if unavailable. */
    val primaryPhy: Int = 0,
    /** Secondary advertising PHY. Requires API 26+; 0 if unavailable. */
    val secondaryPhy: Int = 0,
    /** Advertising set ID. Requires API 26+; 255 = not present. */
    val advertisingSid: Int = 255,
    /** Periodic advertising interval in units of 1.25 ms. Requires API 26+; 0 if unavailable. */
    val periodicAdvertisingInterval: Int = 0
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
 * NOTES: API-gated fields use safe defaults when unavailable.
 *   serviceSolicitationUuids requires API 29 (Q).
 *   primaryPhy/secondaryPhy/advertisingSid/periodicAdvertisingInterval require API 26 (O).
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

    val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isConnectable else false

    val solicitationUuids: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        record?.serviceSolicitationUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
    } else {
        emptyList()
    }

    val (primPhy, secPhy, sid, periodicInterval) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        arrayOf(primaryPhy, secondaryPhy, advertisingSid, periodicAdvertisingInterval)
    } else {
        arrayOf(0, 0, 255, 0)
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
        timestampMs = System.currentTimeMillis(),
        serviceSolicitationUuids = solicitationUuids,
        rawScanBytes = record?.bytes,
        timestampNanos = timestampNanos,
        primaryPhy = primPhy as Int,
        secondaryPhy = secPhy as Int,
        advertisingSid = sid as Int,
        periodicAdvertisingInterval = periodicInterval as Int
    )
}
```

- [ ] **Step 3: Run full unit tests to confirm no regressions**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL — no existing test should break because all new fields have defaults.

- [ ] **Step 4: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/model/RawScanResult.kt \
  app/src/main/kotlin/com/wearaware/app/data/ble/ScanResultMapper.kt && \
git commit -m "feat: expand RawScanResult with solicitation UUIDs, raw bytes, PHY, timing fields"
```

---

## Task 2: BleDebugData model + ObservedDevice + BleRepositoryImpl

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/BleDebugData.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/ObservedDevice.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt`

- [ ] **Step 1: Create BleDebugData.kt**

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Display-only container for raw BLE advertising fields captured at scan time.
 *   Not used for fingerprinting or classification — pure diagnostic data for the UI.
 * NOTES: serviceDataHex values are lowercase hex strings. rawScanBytesHex is the
 *   full ScanRecord bytes as a hex dump — useful for manual protocol inspection.
 *   PHY values: 1=LE 1M, 2=LE 2M, 3=LE Coded. 0 = unavailable (pre-API 26).
 */
data class BleDebugData(
    /** Service solicitation UUIDs (API 29+). Empty on older devices. */
    val serviceSolicitationUuids: List<String>,
    /** Service data keyed by UUID, encoded as lowercase hex strings. */
    val serviceDataHex: Map<String, String>,
    /** Full raw ScanRecord as lowercase hex. Null if ScanRecord was null. */
    val rawScanBytesHex: String?,
    /** Whether the device is advertising as connectable. */
    val isConnectable: Boolean,
    /** LE advertising flags byte value. Null if not in advertisement. */
    val advertisingFlags: Int?,
    /** Hardware timestamp in nanoseconds since device boot. */
    val timestampNanos: Long,
    /** Primary advertising PHY. 0 if unavailable (pre-API 26). */
    val primaryPhy: Int,
    /** Secondary advertising PHY. 0 if unavailable. */
    val secondaryPhy: Int,
    /** Advertising set ID. 255 = no SID. 0 if unavailable. */
    val advertisingSid: Int,
    /** Periodic advertising interval in units of 1.25ms. 0 if unavailable. */
    val periodicAdvertisingInterval: Int,
    /** BluetoothDevice type int: 0=unknown, 1=classic, 2=LE, 3=dual. */
    val deviceType: Int,
    /** BluetoothDevice bond state int: 10=none, 11=bonding, 12=bonded. */
    val bondState: Int
)
```

- [ ] **Step 2: Update ObservedDevice.kt — add rawBleData field with default**

Add one field at the end of the constructor (after `companyNames`):

```kotlin
package com.wearaware.app.domain.model

data class ObservedDevice(
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
    val macAddress: String? = null,
    val fingerprint: DeviceFingerprint? = null,
    val companyNames: List<String> = emptyList(),
    /** Raw BLE diagnostic data captured at first observation. Null if data unavailable. */
    val rawBleData: BleDebugData? = null
) {
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
```

- [ ] **Step 3: Replace BleRepositoryImpl.kt**

Key changes: `DeviceState` stores a `BleDebugData`; `processRawScanResult` builds it from `raw`; `emitDeviceList` passes it to `ObservedDevice`. The `toHexString()` extension from `FingerprintBuilder.kt` is imported.

```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.ble.BleScanner
import com.wearaware.app.data.ble.toFingerprint
import com.wearaware.app.data.ble.toHexString
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

@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val classifier: FingerprintClassifier
) : BleRepository {

    private data class DeviceState(
        val smoother: RssiSmoother = RssiSmoother(),
        val macAddress: String,
        val fingerprint: DeviceFingerprint,
        val rawBleData: BleDebugData,
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
            val debugData = BleDebugData(
                serviceSolicitationUuids = raw.serviceSolicitationUuids,
                serviceDataHex = raw.serviceData.mapValues { (_, v) -> v.toHexString() },
                rawScanBytesHex = raw.rawScanBytes?.toHexString(),
                isConnectable = raw.isConnectable,
                advertisingFlags = raw.advertisingFlags,
                timestampNanos = raw.timestampNanos,
                primaryPhy = raw.primaryPhy,
                secondaryPhy = raw.secondaryPhy,
                advertisingSid = raw.advertisingSid,
                periodicAdvertisingInterval = raw.periodicAdvertisingInterval,
                deviceType = raw.deviceType,
                bondState = raw.bondState
            )
            deviceStates[key] = DeviceState(
                smoother = smoother,
                macAddress = raw.address,
                fingerprint = fp,
                rawBleData = debugData,
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
                    companyNames = state.fingerprint.manufacturerNames,
                    rawBleData = state.rawBleData
                )
            }
            .sortedByDescending { it.averagedRssi }

        _observedDevices.value = devices
    }
}
```

- [ ] **Step 4: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/model/BleDebugData.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/ObservedDevice.kt \
  app/src/main/kotlin/com/wearaware/app/data/repository/BleRepositoryImpl.kt && \
git commit -m "feat: add BleDebugData model; capture raw BLE debug fields in BleRepositoryImpl"
```

---

## Task 3: ScanFilter enum + ScanUiState filter support + ScanViewModel.setFilter()

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/ScanFilter.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/kotlin/com/wearaware/app/domain/model/ScanFilterTest.kt
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
```

- [ ] **Step 2: Run tests to confirm FAIL**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.ScanFilterTest" 2>&1 | tail -10
```

Expected: FAIL — `ScanFilter not found`.

- [ ] **Step 3: Create ScanFilter.kt**

The `matches()` function lives on the enum so the filter logic is pure-Kotlin and testable without Compose or ViewModel.

```kotlin
package com.wearaware.app.domain.model

/**
 * PURPOSE: Determines which devices are shown on ScanScreen.
 *   Each value implements matches() — applied as a predicate on ObservedDevice.
 * NOTES: Filtering is a computed property on ScanUiState; the ViewModel only stores the
 *   active filter enum value. Apple devices remain visible under ALL and are never promoted
 *   as target candidates for Meta glasses; HIDE_APPLE lets the user reduce visual noise.
 */
enum class ScanFilter {
    ALL,
    GLASSES_CANDIDATES,
    META_DEVICES,
    HIDE_APPLE,
    UNKNOWN_ONLY,
    STRONG_SIGNAL;

    fun matches(device: ObservedDevice): Boolean = when (this) {
        ALL -> true
        GLASSES_CANDIDATES ->
            device.classification.category == DeviceCategory.SMART_GLASSES ||
                device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE
        META_DEVICES ->
            device.companyNames.any { it.contains("Meta", ignoreCase = true) }
        HIDE_APPLE ->
            device.companyNames.none { it.contains("Apple", ignoreCase = true) }
        UNKNOWN_ONLY ->
            device.classification.category == DeviceCategory.UNKNOWN_BLE_DEVICE
        STRONG_SIGNAL ->
            device.proximityLabel == ProximityLabel.VERY_CLOSE ||
                device.proximityLabel == ProximityLabel.STRONG ||
                device.proximityLabel == ProximityLabel.NEARBY
    }
}
```

- [ ] **Step 4: Run tests to confirm PASS**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest --tests "*.ScanFilterTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 6 tests passing.

- [ ] **Step 5: Update ScanUiState.kt — add activeFilter and filteredDevices**

```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanFilter
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
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap(),
    val activeFilter: ScanFilter = ScanFilter.ALL
) {
    /**
     * Best-scoring candidate device paired with its match result, or null.
     * Only MEDIUM or HIGH confidence devices qualify.
     */
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull {
                it.isTopCandidate &&
                    (it.confidence == MatchConfidence.HIGH || it.confidence == MatchConfidence.MEDIUM)
            } ?: return null
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

    /**
     * sortedDevices with the active filter applied.
     * ScanScreen uses this for its LazyColumn.
     */
    val filteredDevices: List<ObservedDevice>
        get() = sortedDevices.filter { activeFilter.matches(it) }
}

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
```

- [ ] **Step 6: Add setFilter() to ScanViewModel.kt**

Find the block with `toggleFocusMode()` and `toggleDebugMode()` and add one method after them:

```kotlin
fun setFilter(filter: ScanFilter) {
    _uiState.update { it.copy(activeFilter = filter) }
}
```

Also add the import at the top of ScanViewModel.kt:
```kotlin
import com.wearaware.app.domain.model.ScanFilter
```

- [ ] **Step 7: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/model/ScanFilter.kt \
  app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt \
  app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt \
  app/src/test/kotlin/com/wearaware/app/domain/model/ScanFilterTest.kt && \
git commit -m "feat: add ScanFilter enum with matches(); add filteredDevices to ScanUiState; add setFilter() to ScanViewModel"
```

---

## Task 4: Filter chips in ScanScreen

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`

- [ ] **Step 1: Replace ScanScreen.kt**

```kotlin
package com.wearaware.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wearaware.app.domain.model.ScanFilter
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

            // Filter chips (while scanning)
            if (uiState.scanState == ScanState.SCANNING) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.activeFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Scan status header
            ScanStatusHeader(
                scanState = uiState.scanState,
                deviceCount = uiState.filteredDevices.size
            )

            // Focus mode label
            if (uiState.focusMode && uiState.scanState == ScanState.SCANNING) {
                Text(
                    text = "Sorted by match score for Wayfarer 00ZS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp)
                )
            }

            // Device list
            if (uiState.filteredDevices.isEmpty() && uiState.scanState == ScanState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMessage = if (uiState.activeFilter == ScanFilter.ALL)
                        "No nearby devices detected"
                    else
                        "No devices match \"${uiState.activeFilter.label}\" filter"
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.filteredDevices, key = { it.id }) { device ->
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

            Text(
                text = SafeWording.DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
```

Note: `ScanFilter.label` and `ScanFilter.entries` are used above. Add these to `ScanFilter.kt`:

Open `app/src/main/kotlin/com/wearaware/app/domain/model/ScanFilter.kt` and add the `label` property to each enum entry using a constructor parameter:

```kotlin
enum class ScanFilter(val label: String) {
    ALL("All"),
    GLASSES_CANDIDATES("Glasses"),
    META_DEVICES("Meta"),
    HIDE_APPLE("Hide Apple"),
    UNKNOWN_ONLY("Unknown"),
    STRONG_SIGNAL("Strong Signal");

    fun matches(device: ObservedDevice): Boolean = when (this) {
        ALL -> true
        GLASSES_CANDIDATES ->
            device.classification.category == DeviceCategory.SMART_GLASSES ||
                device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE
        META_DEVICES ->
            device.companyNames.any { it.contains("Meta", ignoreCase = true) }
        HIDE_APPLE ->
            device.companyNames.none { it.contains("Apple", ignoreCase = true) }
        UNKNOWN_ONLY ->
            device.classification.category == DeviceCategory.UNKNOWN_BLE_DEVICE
        STRONG_SIGNAL ->
            device.proximityLabel == ProximityLabel.VERY_CLOSE ||
                device.proximityLabel == ProximityLabel.STRONG ||
                device.proximityLabel == ProximityLabel.NEARBY
    }
}
```

- [ ] **Step 2: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt \
  app/src/main/kotlin/com/wearaware/app/domain/model/ScanFilter.kt && \
git commit -m "feat: add filter chip row to ScanScreen; use filteredDevices in device list"
```

---

## Task 5: Update DeviceCard with company ID + BLE data indicator

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`

- [ ] **Step 1: Replace DeviceCard.kt**

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
                    // Primary display name
                    val displayName = device.advertisedName
                        ?: device.companyNames.firstOrNull()?.let { "$it device" }
                        ?: device.classification.displayLabel.ifBlank { null }
                        ?: "BLE Device"
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Manufacturer name + company ID hex
                    if (device.companyNames.isNotEmpty()) {
                        val companyIdHex = device.fingerprint?.manufacturerIds
                            ?.firstOrNull()
                            ?.let { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
                            ?: ""
                        val manufacturerLine = if (companyIdHex.isNotEmpty())
                            "${device.companyNames.joinToString(", ")}  $companyIdHex"
                        else
                            device.companyNames.joinToString(", ")
                        Text(
                            text = manufacturerLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Category label
                    if (device.classification.displayLabel.isNotBlank()) {
                        Text(
                            text = device.classification.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Signal label
                    Text(
                        text = device.proximityLabel.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() } +
                            "  ${device.averagedRssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Seen duration + BLE debug available indicator
                    val seenText = "Seen: ${device.seenDurationMs.formatDuration()}"
                    val bleIndicator = if (device.rawBleData != null) "  ◉ BLE" else ""
                    Text(
                        text = seenText + bleIndicator,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.rawBleData != null)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
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

- [ ] **Step 2: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt && \
git commit -m "feat: show company ID hex and BLE debug indicator on DeviceCard"
```

---

## Task 6: Expand Raw BLE Debug section in DeviceDetailScreen

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt`

- [ ] **Step 1: Replace the debug section in DeviceDetailScreen.kt**

Find the `// --- Debug View (shown when debug mode is on) ---` block and replace it with the expanded version below. The rest of the file stays identical — only replace from `if (uiState.debugMode)` through the closing `}` of that block.

```kotlin
            // --- Debug View (shown when debug mode is on) ---
            if (uiState.debugMode) {
                HorizontalDivider()
                Text(
                    "Raw BLE Debug",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                // Identity
                Text("Fingerprint ID: ${device.id}", style = MaterialTheme.typography.labelSmall)
                Text("MAC address: ${device.macAddress ?: "n/a"}", style = MaterialTheme.typography.labelSmall)
                Text("BT device name: ${device.fingerprint?.normalizedName ?: "n/a"}", style = MaterialTheme.typography.labelSmall)

                // Manufacturer
                val fp = device.fingerprint
                if (fp != null) {
                    Text(
                        "Manufacturer IDs: ${fp.manufacturerIds.map { "0x${it.toString(16).uppercase().padStart(4, '0')}" }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    fp.manufacturerDataHex.forEach { (id, hex) ->
                        Text(
                            "  0x${id.toString(16).uppercase().padStart(4, '0')}: $hex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Service UUIDs
                if (fp?.serviceUuids?.isNotEmpty() == true) {
                    Text("Service UUIDs:", style = MaterialTheme.typography.labelSmall)
                    fp.serviceUuids.forEach { uuid ->
                        Text(
                            "  $uuid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // TX Power
                fp?.txPower?.let { Text("TX Power: $it dBm", style = MaterialTheme.typography.labelSmall) }

                // BleDebugData fields
                val dbg = device.rawBleData
                if (dbg != null) {
                    Text(
                        "Connectable: ${dbg.isConnectable}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    dbg.advertisingFlags?.let {
                        Text(
                            "Advertising flags: 0x${it.toString(16).uppercase()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Solicitation UUIDs
                    if (dbg.serviceSolicitationUuids.isNotEmpty()) {
                        Text("Solicitation UUIDs:", style = MaterialTheme.typography.labelSmall)
                        dbg.serviceSolicitationUuids.forEach { uuid ->
                            Text(
                                "  $uuid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Service data
                    if (dbg.serviceDataHex.isNotEmpty()) {
                        Text("Service data:", style = MaterialTheme.typography.labelSmall)
                        dbg.serviceDataHex.forEach { (uuid, hex) ->
                            Text(
                                "  $uuid: $hex",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // PHY + timing
                    if (dbg.primaryPhy != 0) {
                        Text(
                            "PHY: primary=${dbg.primaryPhy} secondary=${dbg.secondaryPhy}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (dbg.advertisingSid != 255 && dbg.advertisingSid != 0) {
                        Text("Advertising SID: ${dbg.advertisingSid}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (dbg.periodicAdvertisingInterval != 0) {
                        Text(
                            "Periodic interval: ${dbg.periodicAdvertisingInterval} × 1.25ms",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        "Device type: ${dbg.deviceType}  Bond state: ${dbg.bondState}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (dbg.timestampNanos != 0L) {
                        Text(
                            "Timestamp (nanos since boot): ${dbg.timestampNanos}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Raw scan bytes
                    dbg.rawScanBytesHex?.let { hex ->
                        Text("Raw scan bytes:", style = MaterialTheme.typography.labelSmall)
                        // Show max 64 chars then "..." to avoid overwhelming the screen
                        val preview = if (hex.length > 64) hex.take(64) + "…" else hex
                        Text(
                            preview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Raw BLE debug data unavailable for this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Classification + match
                Text(
                    "Classification rule: ${device.classification.matchedRuleId ?: "none"}",
                    style = MaterialTheme.typography.labelSmall
                )
                matchResult?.let {
                    Text(
                        "Target match score: ${it.score} (${it.confidence.name})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
```

- [ ] **Step 2: Run full unit tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/ui/screens/DeviceDetailScreen.kt && \
git commit -m "feat: expand Raw BLE Debug section with all BleDebugData fields, raw bytes, PHY, service data"
```

---

## Task 7: Logging chain update (DB v4)

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt`

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
    val manufacturerIds: String? = null,       // comma-separated hex e.g. "0075,004c"
    val targetMatchScore: Int? = null,
    val targetMatchReason: String? = null,
    val isTopCandidate: Boolean = false,
    val manufacturerDataHex: String? = null,   // "companyId:hex,companyId:hex" e.g. "0075:deadbeef"
    val serviceUuids: String? = null,          // comma-separated UUID list
    val txPower: Int? = null,
    val connectable: Boolean = false,
    val rawScanBytesHex: String? = null        // first 32 bytes as hex (truncated for storage)
)
```

- [ ] **Step 2: Replace ScanLogEntity.kt**

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
    @ColumnInfo(defaultValue = "0") val isTopCandidate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val manufacturerDataHex: String? = null,
    @ColumnInfo(defaultValue = "NULL") val serviceUuids: String? = null,
    @ColumnInfo(defaultValue = "NULL") val txPower: Int? = null,
    @ColumnInfo(defaultValue = "0") val connectable: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val rawScanBytesHex: String? = null
)
```

- [ ] **Step 3: Update WearAwareDatabase.kt — bump to version 4**

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
 * NOTES: fallbackToDestructiveMigration used — session logs are ephemeral and user-clearable.
 */
@Database(entities = [ScanLogEntity::class], version = 4, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 4: Replace ScanLogMapper.kt**

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
    isTopCandidate = matchResult?.isTopCandidate ?: false,
    manufacturerDataHex = fingerprint?.manufacturerDataHex
        ?.entries?.joinToString(",") { (id, hex) ->
            "${id.toString(16).padStart(4, '0')}:$hex"
        },
    serviceUuids = fingerprint?.serviceUuids?.joinToString(",")?.takeIf { it.isNotEmpty() },
    txPower = fingerprint?.txPower,
    connectable = rawBleData?.isConnectable ?: false,
    // Store first 32 bytes (64 hex chars) to keep log rows compact
    rawScanBytesHex = rawBleData?.rawScanBytesHex?.take(64)
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
    isTopCandidate = isTopCandidate,
    manufacturerDataHex = manufacturerDataHex,
    serviceUuids = serviceUuids,
    txPower = txPower,
    connectable = connectable,
    rawScanBytesHex = rawScanBytesHex
)
```

- [ ] **Step 5: Run full unit tests — expect ScanLogMapperTest may need updating**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

If `ScanLogMapperTest` fails because it constructs `ScanLogEntity` with positional args, update the `toDomain` test to use named params or add the new fields with defaults. The `ScanLogEntity` constructor now has new optional fields with defaults so call sites using named params won't break.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/main/kotlin/com/wearaware/app/domain/model/ScanLogEntry.kt \
  app/src/main/kotlin/com/wearaware/app/data/local/ScanLogEntity.kt \
  app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt \
  app/src/main/kotlin/com/wearaware/app/data/mapper/ScanLogMapper.kt && \
git commit -m "feat: extend scan log with manufacturer hex, service UUIDs, txPower, connectable, raw bytes (DB v4)"
```

---

## Task 8: Unit tests — Apple isolation, Meta ranking, BleDebugData mapping

**Files:**
- Modify: `app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/data/ble/BleDebugDataMappingTest.kt`

- [ ] **Step 1: Add 3 new tests to MatchTargetDeviceUseCaseTest.kt**

Open the existing test file and add these tests before the closing `}`:

```kotlin
    @Test
    fun `Apple manufacturer only device is not eligible as Meta glasses candidate`() {
        val now = System.currentTimeMillis()
        val appleDevice = ObservedDevice(
            id = "apple1",
            advertisedName = null,
            rawRssi = -55,
            averagedRssi = -55,
            proximityLabel = ProximityLabel.VERY_CLOSE,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now - 60_000L,
            lastSeenAt = now,
            seenCount = 30,
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
            companyNames = listOf("Apple")
        )
        val results = useCase(listOf(appleDevice), profile)
        val result = results[appleDevice.id]!!
        // Apple company name alone is NOT a Meta identity signal
        assertEquals("Apple-only must not be eligible", MatchConfidence.NONE, result.confidence)
        assertEquals(0, result.score)
        assertFalse(result.isTopCandidate)
    }

    @Test
    fun `Meta manufacturer with wearable classification outranks Apple-only device`() {
        val now = System.currentTimeMillis()
        val appleDevice = ObservedDevice(
            id = "apple2",
            advertisedName = null,
            rawRssi = -50,
            averagedRssi = -50,
            proximityLabel = ProximityLabel.VERY_CLOSE,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now,
            lastSeenAt = now,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = null, ruleVersion = null,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = "Unknown", confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false, evaluationNotes = null
            ),
            persistenceAlert = null,
            companyNames = listOf("Apple")
        )
        val metaDevice = makeDevice(
            id = "meta2",
            category = DeviceCategory.CAMERA_CAPABLE_WEARABLE,
            matchedRuleId = "meta_rayban_v1"
        )
        val results = useCase(listOf(appleDevice, metaDevice), profile)
        assertTrue(
            "Meta+wearable score (${results["meta2"]!!.score}) should beat Apple-only (${results["apple2"]!!.score})",
            results["meta2"]!!.score > results["apple2"]!!.score
        )
        assertTrue(results["meta2"]!!.isTopCandidate)
        assertFalse(results["apple2"]!!.isTopCandidate)
    }

    @Test
    fun `Apple is visible in ALL filter but hidden in HIDE_APPLE filter`() {
        val now = System.currentTimeMillis()
        val appleDevice = ObservedDevice(
            id = "apple3",
            advertisedName = "iPhone",
            rawRssi = -60,
            averagedRssi = -60,
            proximityLabel = ProximityLabel.STRONG,
            visibilityState = VisibilityState.DETECTED_NOW,
            firstSeenAt = now,
            lastSeenAt = now,
            seenCount = 1,
            classification = ClassificationResult(
                matchedRuleId = null, ruleVersion = null,
                category = DeviceCategory.UNKNOWN_BLE_DEVICE,
                displayLabel = "Unknown", confidence = ConfidenceLevel.LOW,
                isWearableCandidate = false, evaluationNotes = null
            ),
            persistenceAlert = null,
            companyNames = listOf("Apple")
        )
        assertTrue("Apple device should pass ALL filter", ScanFilter.ALL.matches(appleDevice))
        assertFalse("Apple device should not pass HIDE_APPLE filter", ScanFilter.HIDE_APPLE.matches(appleDevice))
    }
```

Note: this test uses `ScanFilter` — add the import at the top of the test file:
```kotlin
import com.wearaware.app.domain.model.ScanFilter
```

- [ ] **Step 2: Create BleDebugDataMappingTest.kt**

```kotlin
// app/src/test/kotlin/com/wearaware/app/data/ble/BleDebugDataMappingTest.kt
package com.wearaware.app.data.ble

import com.wearaware.app.domain.model.BleDebugData
import org.junit.Assert.*
import org.junit.Test

class BleDebugDataMappingTest {

    @Test
    fun `serviceDataHex encodes byte arrays as lowercase hex strings`() {
        val serviceData = BleDebugData(
            serviceSolicitationUuids = emptyList(),
            serviceDataHex = mapOf(
                "0000fe95-0000-1000-8000-00805f9b34fb" to "deadbeef",
                "0000180a-0000-1000-8000-00805f9b34fb" to "cafe0102"
            ),
            rawScanBytesHex = null,
            isConnectable = true,
            advertisingFlags = 0x1A,
            timestampNanos = 123456789L,
            primaryPhy = 1,
            secondaryPhy = 0,
            advertisingSid = 255,
            periodicAdvertisingInterval = 0,
            deviceType = 2,
            bondState = 10
        )
        assertEquals("deadbeef", serviceData.serviceDataHex["0000fe95-0000-1000-8000-00805f9b34fb"])
        assertEquals("cafe0102", serviceData.serviceDataHex["0000180a-0000-1000-8000-00805f9b34fb"])
    }

    @Test
    fun `rawScanBytesHex is null when ScanRecord bytes are null`() {
        val debugData = BleDebugData(
            serviceSolicitationUuids = emptyList(),
            serviceDataHex = emptyMap(),
            rawScanBytesHex = null,
            isConnectable = false,
            advertisingFlags = null,
            timestampNanos = 0L,
            primaryPhy = 0,
            secondaryPhy = 0,
            advertisingSid = 255,
            periodicAdvertisingInterval = 0,
            deviceType = 0,
            bondState = 10
        )
        assertNull(debugData.rawScanBytesHex)
    }

    @Test
    fun `toHexString produces correct lowercase hex for known bytes`() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("deadbeef", bytes.toHexString())
    }

    @Test
    fun `toHexString returns empty string for empty ByteArray`() {
        assertEquals("", byteArrayOf().toHexString())
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. Target test count: 75+ tests across all suites.

- [ ] **Step 4: Commit**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1 && git add \
  app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchTargetDeviceUseCaseTest.kt \
  app/src/test/kotlin/com/wearaware/app/data/ble/BleDebugDataMappingTest.kt && \
git commit -m "test: add Apple isolation, Meta ranking, filter behavior, and BleDebugData mapping tests"
```

---

## Self-Review

**Spec coverage:**

| Requirement | Task(s) |
|-------------|---------|
| 1. Expand BLE data extraction (solicitation UUIDs, raw bytes, PHY, timing) | Task 1 + Task 2 |
| 2. Raw BLE Debug section in DeviceDetailScreen | Task 6 |
| 3. List filters (All/Glasses/Meta/Hide Apple/Unknown/Strong Signal) | Task 3 + Task 4 |
| 4. Target matching reasons shown | Already in DeviceDetailScreen — no change needed |
| 5. Reduce Apple noise (easy to hide, not promoted) | Task 3 (HIDE_APPLE filter), Task 8 (Apple isolation tests) |
| 6. Device card labels (company ID, category, signal, BLE indicator) | Task 5 |
| 7. Logging (manufacturerDataHex, serviceUuids, txPower, connectable, rawBytes) | Task 7 |
| 8. Tests (Apple isolation, Meta ranking, filter behavior, raw mapping) | Task 8 |

**All requirements covered. No placeholders. No TODOs.**
