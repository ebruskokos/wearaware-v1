# Target Capture and Compare — Design Spec

**Date:** 2026-04-04
**Feature:** Target Capture and Compare mode for WearAware
**Goal:** Allow the user to identify their Meta Ray-Ban glasses among nearby BLE devices by comparing a baseline scan (glasses OFF) against a target scan (glasses ON), then ranking candidates by differential evidence.

---

## 1. Overview

The user runs two deliberately timed BLE captures:

1. **Baseline capture** — glasses powered OFF, to establish what BLE devices are normally present in the environment.
2. **Target capture** — glasses powered ON and nearby, to observe which new devices appear or which existing devices change significantly.

A compare engine diffs the two captures and ranks candidates by score and confidence. The top candidate is shown with the reasoning behind the score, using safe wording that never claims certainty.

Exactly one baseline and one target capture are stored at a time. Starting a new capture of either type replaces the previous one.

**Only one capture may be active at a time.** Baseline and target captures cannot overlap — if the user attempts to start one while the other is already running, the UI shows a clear message ("Stop the active capture before starting a new one") and takes no action.

---

## 2. Architecture

### Layer boundaries

- **BLE parsing** stays entirely in the data layer (`BleScanner`, `ScanResultMapper`, `BleRepositoryImpl`). The capture subsystem never touches raw BLE types.
- **Capture accumulation and compare logic** live in the domain layer (`CompareCapturesUseCase`). Pure Kotlin — no Android dependencies.
- **Persistence** lives in the data layer (`CaptureRepositoryImpl`, Room entities, `CaptureDao`).
- **ViewModels only orchestrate**: `CaptureViewModel` observes `BleRepository.observedDevices` (same stream as `ScanViewModel`), accumulates in memory, and delegates persistence to `CaptureRepository`.

### New files

**Domain layer:**
```
domain/model/CaptureType.kt
domain/model/CapturedDevice.kt
domain/model/CaptureSession.kt
domain/model/CompareConfidence.kt
domain/model/CompareMatchResult.kt
domain/repository/CaptureRepository.kt
domain/usecase/CompareCapturesUseCase.kt
```

**Data layer:**
```
data/local/CaptureSessionEntity.kt
data/local/CapturedDeviceEntity.kt
data/local/CaptureDao.kt
data/mapper/CaptureMapper.kt
data/repository/CaptureRepositoryImpl.kt
```

**UI layer:**
```
ui/viewmodel/CaptureUiState.kt
ui/viewmodel/CaptureViewModel.kt
ui/screens/CaptureScreen.kt
```

**Modified files (minimal):**
```
data/local/WearAwareDatabase.kt      — add two entities, version 4 → 5
di/DatabaseModule.kt                 — add provideCaptureDao()
di/RepositoryModule.kt               — bind CaptureRepository
ui/navigation/NavGraph.kt            — add Screen.Capture; add "Compare" button to ScanScreen top bar
```

**Test files:**
```
test/.../domain/usecase/CompareCapturesUseCaseTest.kt
test/.../data/repository/CaptureRepositoryImplTest.kt
```

---

## 3. Domain Models

### `CaptureType`
```kotlin
enum class CaptureType { BASELINE, TARGET }
```

### `CapturedDevice`
One entry per device observed during a capture window. Fields are accumulated across the full window, not snapshotted at stop time.

```kotlin
data class CapturedDevice(
    val fingerprintId: String,          // stable content-hash identity
    val advertisedName: String?,
    val macAddress: String?,            // display only — may be randomized
    val manufacturerIds: List<Int>,     // Bluetooth SIG company IDs (e.g. 0x0075 = Meta)
    val manufacturerDataSummary: String?,  // "0075:deadbeef,004c:..." for display
    val serviceUuids: List<String>,
    val category: DeviceCategory,       // from ClassificationResult
    val companyNames: List<String>,     // resolved names e.g. ["Meta"], ["Apple"]
    val firstSeenInCapture: Long,       // epoch ms — when first observed in this window
    val lastSeenInCapture: Long,        // epoch ms — most recent observation
    val peakRssi: Int,                  // strongest single reading during window
    val averageRssi: Int,               // mean of all readings during window
    val seenCount: Int,                 // number of scan ticks this device was present
    val visibleAtStop: Boolean,         // true if DETECTED_NOW when Stop was pressed
    val targetMatchScore: Int?,         // from MatchTargetDeviceUseCase at first observation
    val targetMatchSignals: List<String> // signal strings from MatchTargetDeviceUseCase
)
```

### `CaptureSession`
```kotlin
data class CaptureSession(
    val id: String,                    // UUID, generated at startBaseline/startTarget
    val type: CaptureType,
    val startedAt: Long,               // epoch ms
    val stoppedAt: Long,               // epoch ms
    val devices: List<CapturedDevice>
)
```

### `CompareConfidence`
```kotlin
enum class CompareConfidence {
    HIGH,    // score >= 9
    MEDIUM,  // score >= 6
    LOW,     // score >= 3
    NONE     // score < 3 — excluded from results
}
```

### `CompareMatchResult`
One per device in the target capture that scores >= 3.

```kotlin
data class CompareMatchResult(
    val capturedDevice: CapturedDevice,
    val score: Int,
    val confidence: CompareConfidence,
    val comparisonSignals: List<String>,  // human-readable reasons, shown in UI
    val seenInBaseline: Boolean,
    val seenInTarget: Boolean,            // always true — these are target devices
    val baselineAverageRssi: Int?,        // null if not seen in baseline
    val rssiDelta: Int?,                  // targetAvg − baselineAvg; null if not in baseline
    val hasBaseline: Boolean              // false when no baseline session was provided
)
```

### `CaptureRepository` interface
```kotlin
interface CaptureRepository {
    suspend fun saveSession(session: CaptureSession)
    suspend fun getSession(type: CaptureType): CaptureSession?
    suspend fun deleteSession(type: CaptureType)
}
```

---

## 4. Capture Flow

### How `CaptureViewModel` accumulates devices

`CaptureViewModel` injects:
- `BleRepository` — same singleton as `ScanViewModel`; no new BLE scanning infrastructure
- `CaptureRepository`
- `MatchTargetDeviceUseCase`
- `CompareCapturesUseCase`

It observes `BleRepository.observedDevices` continuously. Updates are only processed when a capture is actively running (`captureState == CAPTURING`).

**Internal accumulator** (in-memory; discarded after stop):
```
accumulatedDevices: MutableMap<fingerprintId, DeviceAccumulator>
```
`DeviceAccumulator` holds all `CapturedDevice` fields under construction, plus `runningRssiSum` and `readingCount` for average calculation.

### `startBaseline()` / `startTarget()`
1. `captureRepository.deleteSession(type)` — removes previous capture of that type
2. Clear the accumulator map
3. Record `startedAt = now`
4. Set `captureState = CAPTURING` for that type in `CaptureUiState`
5. Begin processing incoming device lists from `observedDevices`

### On each scan tick while capturing
For every `ObservedDevice` in the emitted list:
- **Not yet in accumulator:** Insert new `DeviceAccumulator`. Compute `targetMatchScore` and `targetMatchSignals` via `MatchTargetDeviceUseCase`. Set `firstSeenInCapture = now`, `peakRssi = rawRssi`, `runningRssiSum = rawRssi`, `readingCount = 1`, `seenCount = 1`.
- **Already in accumulator:** Update `lastSeenInCapture = now`, increment `seenCount`, update `peakRssi` if `rawRssi > current peak`, add `rawRssi` to `runningRssiSum`, increment `readingCount`.

### `stopBaseline()` / `stopTarget()`
1. For every device currently in the live `observedDevices` list with `visibilityState == DETECTED_NOW`: mark accumulator entry `visibleAtStop = true`.
2. Snapshot accumulator → `List<CapturedDevice>`. Compute final `averageRssi = runningRssiSum / readingCount` for each entry.
3. Build `CaptureSession(id = UUID.randomUUID().toString(), type, startedAt, stoppedAt = now, devices)`.
4. `captureRepository.saveSession(session)`.
5. Set `captureState = DONE`; store session in `CaptureUiState`.
6. If both baseline and target are `DONE`: automatically invoke `compare()`.

### `compare()`
```kotlin
val results = compareCapturesUseCase(
    baseline = uiState.baseline,
    target = uiState.target!!,
    profile = DefaultTargetProfile.WAYFARER_00ZS
)
_uiState.update { it.copy(compareResults = results) }
```

### `CaptureUiState`
```kotlin
data class CaptureUiState(
    val baselineCaptureState: CaptureState = CaptureState.IDLE,
    val targetCaptureState: CaptureState = CaptureState.IDLE,
    val baseline: CaptureSession? = null,
    val target: CaptureSession? = null,
    val compareResults: List<CompareMatchResult> = emptyList()
)

enum class CaptureState { IDLE, CAPTURING, DONE }
```

---

## 5. Compare Engine (`CompareCapturesUseCase`)

### Signature
```kotlin
operator fun invoke(
    baseline: CaptureSession?,
    target: CaptureSession,
    profile: TargetDeviceProfile
): List<CompareMatchResult>
```

Returns results sorted using a deterministic comparator:
1. Score descending (primary)
2. Exact target name match present — first among ties
3. Meta manufacturer match present — `manufacturerIds` contains `0x0075`
4. `targetAverageRssi` descending
5. `fingerprintId` ascending (final stable tie-break)

Devices scoring < 3 (`NONE`) are excluded.

**Top-candidate eligibility:** Only the single highest-scoring result with confidence `MEDIUM` or `HIGH` may be flagged as top candidate. `LOW` confidence results appear in the list but receive no "★ Most likely" label.

### Scoring table

For each device in `target.devices`, look up the same `fingerprintId` in `baseline.devices` → `baselineDevice` (nullable).

| Signal | Points | Condition |
|---|---|---|
| Only in target — with identity signal | +8 | `baselineDevice == null` AND at least one of: Meta manufacturer, wearable classification, or profile name match fires |
| Only in target — no identity signal | +3 | `baselineDevice == null` AND none of the above (caps at LOW alone) |
| Strong RSSI increase | +4 | `baselineDevice != null` AND `target.averageRssi − baseline.averageRssi >= 10` dBm |
| Exact target name match | +4 | `advertisedName` equals `profile.friendlyName` (case-insensitive) |
| Meta manufacturer | +3 | `manufacturerIds` contains `0x0075` — **canonical field for logic; `companyNames` is display-only** |
| `SMART_GLASSES` classification | +3 | `category == DeviceCategory.SMART_GLASSES` |
| `CAMERA_CAPABLE_WEARABLE` classification | +2 | `category == DeviceCategory.CAMERA_CAPABLE_WEARABLE` |
| Partial profile hint match | +2 | `advertisedName` contains `profile.modelHint` or `profile.brandHint` (case-insensitive) |
| Persistent during target | +1 | `seenCount >= 5` during target capture |

Signals are additive. Each fired signal adds a human-readable string to `comparisonSignals`.

### Confidence thresholds
```
HIGH   >= 9
MEDIUM >= 6
LOW    >= 3
NONE   < 3 → excluded
```

### Edge cases

- **`baseline == null`:** The "only in target" signals (+8 / +3) never fire. All other signals (Meta, classification, name, persistence) still score. Results carry `hasBaseline = false` — the UI shows a weaker-evidence disclaimer. A Meta wearable without a baseline can score up to 9 (MEDIUM/HIGH) via other signals.
- **All devices also in baseline with no RSSI change and no identity signals:** Most score 0 (excluded). Environment noise is naturally suppressed.
- **Empty target:** Returns empty list.

---

## 6. Storage — Room Tables

### DB version: 4 → 5
`fallbackToDestructiveMigration` is already configured. Old scan logs are ephemeral and acceptable to clear.

### `capture_session` table

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PRIMARY KEY | UUID string |
| `type` | TEXT | `"BASELINE"` or `"TARGET"` |
| `started_at` | INTEGER | epoch ms |
| `stopped_at` | INTEGER | epoch ms |

### `captured_device` table

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PRIMARY KEY autoincrement | |
| `session_id` | TEXT | references `capture_session.id` |
| `fingerprint_id` | TEXT | |
| `advertised_name` | TEXT NULL | |
| `mac_address` | TEXT NULL | display only |
| `manufacturer_ids` | TEXT NULL | comma-sep hex e.g. `"0075,004c"` |
| `manufacturer_data_summary` | TEXT NULL | `"0075:deadbeef,..."` |
| `service_uuids` | TEXT NULL | comma-sep |
| `category` | TEXT | `DeviceCategory.name` |
| `company_names` | TEXT NULL | comma-sep |
| `first_seen_in_capture` | INTEGER | epoch ms |
| `last_seen_in_capture` | INTEGER | epoch ms |
| `peak_rssi` | INTEGER | |
| `average_rssi` | INTEGER | |
| `seen_count` | INTEGER | |
| `visible_at_stop` | INTEGER | 0/1 boolean |
| `target_match_score` | INTEGER NULL | from `MatchTargetDeviceUseCase` |
| `target_match_signals` | TEXT NULL | semicolon-separated signal strings |

### `CaptureDao`
```kotlin
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
```

`saveSession()` inserts the session row first, then all device rows.
`deleteSession()` calls `deleteDevicesForSession` then `deleteSession` in sequence. No Room FK cascade required.

---

## 7. UI — `CaptureScreen`

### Navigation

`Screen.Capture("capture")` added to `NavGraph`. A `"Compare"` `TextButton` in the `ScanScreen` top bar navigates to it (same style as existing "Focus Mode" and "Debug" buttons). `CaptureViewModel` is scoped to the `Screen.Capture` back-stack entry, independent of `ScanViewModel`.

### Layout — single scrollable column

**Section 1: Baseline Capture**
- Header: "Step 1: Baseline (glasses OFF)"
- Subtext: "Scan the environment before powering on your target device."
- Status chip: IDLE / CAPTURING / DONE (N devices, Xs)
- Button: "Start Baseline" → while running → "Stop Baseline"
- When DONE: "Captured N devices over Xs"

**Section 2: Target Capture**
- Header: "Step 2: Target (glasses ON and nearby)"
- Subtext: "Power on your target device, then start this capture."
- Status chip: IDLE / CAPTURING / DONE (N devices, Xs)
- Button: "Start Target" → while running → "Stop Target"
- When DONE: "Captured N devices over Xs"
- Baseline section dims while target is capturing.

**Section 3: Compare Results**

Shown only when target capture is DONE. Triggered automatically on stop.

**No baseline captured:**
```
ℹ "No baseline was captured. Results show target-only evidence
   without differential comparison — treat with lower confidence."
```

**No strong match:**
```
"No strong differential match found yet.
 Try a longer capture or move closer to your target device."
```

**Per candidate card** (sorted by score desc, NONE excluded):

```
[HIGH / MEDIUM / LOW badge]
Display name
Manufacturer: Meta  0x0075

Seen in baseline: No    Seen in target: Yes
RSSI: baseline n/a → target −62 dBm
  (or: baseline −75 → target −62  Δ +13 dBm)

Why this device:
  • Only appeared when target was powered on
  • Meta manufacturer match (0x0075)
  • Classification: SMART_GLASSES
  • ...

Manufacturer data: 0075:deadbeef...
Service UUIDs: 0000fe2c-...

[ View Full Device Details ]   (only shown if device still in live scan)
```

**Top candidate** (highest score, confidence `MEDIUM` or `HIGH` only — `LOW` confidence results are never highlighted):
```
★ "Most likely new candidate after target device was powered on"
```

### Capture lifecycle enforcement

Only one capture may be active at a time. The "Start Baseline" button is disabled while a target capture is running, and vice versa. If the user attempts to tap a disabled start button, display: "Stop the active capture before starting a new one." Both start buttons are enabled when no capture is running.

### Safe wording rules
- Never say "this is your device." Always say "most likely candidate" or "new candidate."
- Always show the reasoning (`comparisonSignals`), never just a score.
- When `hasBaseline = false`: show the weaker-evidence disclaimer on every result card.
- When results list is empty: show the "No strong differential match" message.

---

## 8. Tests

### `CompareCapturesUseCaseTest` — pure domain, no Android deps

| Test | What it verifies |
|---|---|
| Device only in target with Meta manufacturer → HIGH | +8 (gated) + +3 = 11 → HIGH |
| Device only in target with no identity signal → LOW | +3 only → LOW, doesn't win |
| Device in both with RSSI delta >= 10 → RSSI signal fires | +4 delta added |
| Device in both with RSSI delta < 10 → no RSSI signal | delta signal absent |
| `SMART_GLASSES` scores higher than `CAMERA_CAPABLE_WEARABLE` | +3 vs +2 |
| Exact name match beats partial hint | +4 vs +2 |
| Ranking: highest score is first in results | list ordered descending |
| NONE confidence devices excluded from results | score < 3 → not in list |
| No results with MEDIUM or higher → empty list | UI empty-state path |
| `baseline == null` → "only in target" signals never fire | +8/+3 gate requires non-null baseline |
| `hasBaseline = false` when baseline is null | flag set correctly on all results |
| Meta check uses `manufacturerIds` not `companyNames` | `0x0075` is canonical |
| Persistence bonus fires at `seenCount >= 5` | +1 added |
| Persistence bonus absent at `seenCount < 5` | +1 not added |

### `CaptureRepositoryImplTest` — mapper round-trip

| Test | What it verifies |
|---|---|
| `saveSession` then `getSession` returns equivalent domain object | mapper round-trip correct |
| `deleteSession(BASELINE)` removes session and its devices | cascade delete works |
| `saveSession` with same type replaces previous session | REPLACE strategy on session entity |

---

## 9. Safety and Epistemics

This feature never claims to have identified the user's device. All UI copy uses hedged language:
- "Most likely new candidate after target device was powered on"
- "No strong differential match found yet"
- "Treat with lower confidence" (when no baseline)

The compare engine ranks candidates by differential evidence but does not make a determination. The user sees all the signals that contributed to a score and makes the final judgment. BLE advertising data is inherently incomplete and subject to randomisation; this tool surfaces evidence, not conclusions.
