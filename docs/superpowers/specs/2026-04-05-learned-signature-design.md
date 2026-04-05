# Learned Signature — Design Spec

**Date:** 2026-04-05
**Branch:** feature/wearaware-v1
**Status:** Approved

---

## Problem

The app identifies BLE devices using behavioral scoring (RSSI, persistence, only-in-target) but has no memory across sessions. Every scan starts fresh. The user cannot confirm "this is my glasses" and have the app remember that for future scans.

Raw BLE labels like "Apple device" or "Unknown (...) device" persist as the primary display label even when the user knows the device. The feature described in this spec gives the user a way to confirm a device and have it remembered.

---

## Goal

Allow the user to tap "Learn this device" on the top compare candidate (or its detail screen), save a BLE signal signature for that device, and have the app show "My Meta Glasses" (or "Possible match to your glasses") as the primary label on all future scans — live and compare — without requiring an exact fingerprint match.

---

## Non-Goals

- No user-configurable display name (hardcoded: "My Meta Glasses")
- No multi-device support (one learned signature at a time)
- No cloud sync or cross-device persistence
- No MAC address in the signature (unstable, randomized)

---

## Architecture Overview

```
UI
  CaptureScreen          → "Learn this device" button, status banner, label override
  CapturedDeviceDetailScreen → "Learn this device" button, learned match debug section
  ScanScreen             → status chip, label override on DeviceCard

ViewModel
  CaptureViewModel       → learnDevice(), clearLearnedDevice(), learnedMatchResults in state
  ScanViewModel          → learnedMatchResults in state, updated on every device tick

Domain
  LearnedDeviceSignature (model)
  LearnedMatchResult     (model)
  LearnedConfidence      (enum: STRONG | POSSIBLE | NONE)
  SaveLearnedSignatureUseCase
  MatchLearnedSignatureUseCase
  ClearLearnedSignatureUseCase
  LearnedSignatureRepository (interface)

Data
  LearnedSignatureRepositoryImpl  → SharedPreferences + Gson
```

---

## Data Models

### `LearnedDeviceSignature`

```kotlin
data class LearnedDeviceSignature(
    val displayName: String,                    // always "My Meta Glasses"
    val savedAt: Long,                          // epoch millis
    val fingerprintId: String,                  // bonus signal only — never required
    val manufacturerIds: List<Int>,             // strong signal
    val manufacturerDataPrefixes: List<String>, // very strong signal (first 4 bytes hex per entry)
    val serviceUuids: List<String>              // moderate signal
)
```

**What is NOT stored:** MAC address (randomized), RSSI (session-specific), seenCount (session-specific). Behavior signals are computed fresh on each match.

### `LearnedMatchResult`

```kotlin
data class LearnedMatchResult(
    val signature: LearnedDeviceSignature,
    val score: Int,
    val confidence: LearnedConfidence,
    val matchedSignals: List<String>,   // human-readable reasons, shown in debug section
    val labelOverrideActive: Boolean    // true when confidence == STRONG or POSSIBLE
)

enum class LearnedConfidence { STRONG, POSSIBLE, NONE }
```

---

## Matching Scoring

`MatchLearnedSignatureUseCase` takes a device snapshot + loaded signature and returns a `LearnedMatchResult`.

| Signal | Points | Notes |
|---|---|---|
| Manufacturer data prefix match | +5 per prefix | First 4 bytes of each manufacturer data entry |
| ManufacturerId overlap | +4 if any shared | Not per-ID — flat bonus for any overlap |
| FingerprintId exact match | +3 | Bonus only, never a gate |
| Service UUID overlap | +2 per shared UUID | |
| RSSI ≥ -60 dBm | +2 | Behavior signal — computed fresh |
| seenCount ≥ 50 | +2 | Behavior signal — computed fresh |
| visibleAtStop = true | +1 | Behavior signal — computed fresh |

**Thresholds:**
- `STRONG ≥ 8` → label override: "My Meta Glasses"
- `POSSIBLE ≥ 4` → label: "Possible match to your glasses"
- `NONE < 4` → no override

**Resilience rationale:** A device with no manufacturerIds and no serviceUUIDs can still reach POSSIBLE via behavior signals alone (RSSI + seenCount + visibleAtStop = 5). It cannot reach STRONG without at least one identity signal — this prevents behavioral false positives from overriding the primary label with full confidence.

---

## Persistence

**Implementation:** `LearnedSignatureRepositoryImpl` using `SharedPreferences` + Gson.

**Why not Room:** Adding a Room table requires a schema migration. With `fallbackToDestructiveMigration` in place, a version bump would destroy all existing capture history. For a single persisted record, SharedPreferences is correct.

**Why not DataStore:** Requires a new dependency and async API for a single synchronous read-on-init pattern. Unjustified complexity.

**Repository interface:**
```kotlin
interface LearnedSignatureRepository {
    fun load(): LearnedDeviceSignature?
    fun save(signature: LearnedDeviceSignature)
    fun clear()
}
```

SharedPreferences key: `"learned_device_signature"`. Gson serializes to a JSON string. Missing key returns null.

---

## Use Cases

### `SaveLearnedSignatureUseCase`
- Input: `CapturedDevice`
- Builds `LearnedDeviceSignature` from: `fingerprintId`, `manufacturerIds`, manufacturer data prefixes (first 4 bytes of each entry parsed from `manufacturerDataSummary`), `serviceUuids`, `displayName = "My Meta Glasses"`, `savedAt = System.currentTimeMillis()`
- Calls `repository.save()`
- Logs at DEBUG: fingerprintId, manufacturerIds, manufacturerDataPrefixes, serviceUUIDs

### `MatchLearnedSignatureUseCase`
- Inputs: `LearnedDeviceSignature` + a flat `LearnedMatchInput` data class:
  ```kotlin
  data class LearnedMatchInput(
      val fingerprintId: String,
      val manufacturerIds: List<Int>,
      val manufacturerDataPrefixes: List<String>, // caller extracts before calling
      val serviceUuids: List<String>,
      val averageRssi: Int,
      val seenCount: Int,
      val visibleAtStop: Boolean
  )
  ```
- **Extraction helpers (top-level functions in the use case file):**
  - From `CapturedDevice`: parse `manufacturerDataSummary` ("0075:deadbeef,004c:...") — split by comma, take the hex value part, take first 8 chars (4 bytes)
  - From `ObservedDevice`: iterate `fingerprint?.manufacturerDataHex` (`Map<Int, String>`), take first 8 chars of each value
- Runs scoring table above, builds `LearnedMatchResult`
- Logs at DEBUG: candidate fingerprintId, score, confidence, labelOverrideActive

### `ClearLearnedSignatureUseCase`
- Calls `repository.clear()`

---

## ViewModel Changes

### `CaptureUiState` additions
```kotlin
val learnedSignature: LearnedDeviceSignature? = null,
val learnedMatchResults: Map<String, LearnedMatchResult> = emptyMap(), // keyed by fingerprintId
val learnSaveConfirmation: String? = null,  // shown as snackbar, cleared after 3s
```

### `ScanUiState` additions
```kotlin
val learnedSignature: LearnedDeviceSignature? = null,
val learnedMatchResults: Map<String, LearnedMatchResult> = emptyMap(), // keyed by device id
```

### `CaptureViewModel` additions
- `init`: load signature → store in state → run matching against existing compare results
- `fun learnDevice(fingerprintId: String)`: find device in target session → `SaveLearnedSignatureUseCase` → reload signature → re-run matching → set `learnSaveConfirmation = "Saved as My Meta Glasses"` → clear after 3s via `viewModelScope.launch { delay(3000); _uiState.update { it.copy(learnSaveConfirmation = null) } }`
- `fun clearLearnedDevice()`: `ClearLearnedSignatureUseCase` → clear signature + matchResults from state
- `runCompare()` end: run learned matching against all compare results, update `learnedMatchResults`

### `ScanViewModel` additions
- `init`: load signature → store in state
- On each device list update: run `MatchLearnedSignatureUseCase` for all devices → update `learnedMatchResults`
- Expose `fun reloadLearnedSignature()` — ScanScreen calls this in a `LaunchedEffect(Unit)` on composition so that a signature saved via CaptureScreen (different ViewModel instance) is picked up when the user navigates back to ScanScreen

---

## UI Changes

### `CaptureScreen` / `CompareResultsSection`

**Status banner** (above compare results):
- Signature present: card with green tint — "Learned device profile: My Meta Glasses" + "Clear" text button
- No signature: muted text — "No learned glasses profile saved"

**`CompareResultCard`** label override:
- STRONG or POSSIBLE match: show learned label as `titleLarge` (e.g., "My Meta Glasses")
- Original BLE label (advertisedName / category) moves to secondary `bodySmall` in muted style
- "Learn this device" button on the top candidate card (index == 0), hidden if this device is already the saved signature

**Snackbar:** display `learnSaveConfirmation` when non-null.

### `CapturedDeviceDetailScreen`

- "Learn this device" / "This is my glasses" button (full-width, below identity section)
  - Disabled + label "Already saved as learned device" if `fingerprintId` matches saved signature's `fingerprintId`
- **"Learned Signature Match"** debug section (always visible when signature loaded):
  - Learned signature loaded: Yes — "My Meta Glasses" (or No)
  - Match confidence + score
  - Matched signals (bullet list)
  - Label override active: Yes / No

### `ScanScreen`

- Status chip at top: "My Meta Glasses profile active" when signature loaded
- `DeviceCard`: if STRONG or POSSIBLE match, override displayed name with learned label (same pattern as CaptureScreen)

---

## Tests

### `MatchLearnedSignatureUseCaseTest`
- Matching manufacturerIds + data prefix → STRONG + labelOverrideActive = true
- ManufacturerId match only → POSSIBLE
- No overlap → NONE, labelOverrideActive = false
- FingerprintId match alone does not reach STRONG
- Behavior signals (RSSI, seenCount, visibleAtStop) contribute but cannot alone reach STRONG

### `SaveLearnedSignatureUseCaseTest`
- Saving a `CapturedDevice` produces correct `LearnedDeviceSignature` fields
- `repository.save()` called exactly once

### `ClearLearnedSignatureUseCaseTest`
- `repository.clear()` called; subsequent `load()` returns null

### `LearnedSignatureRepositoryImplTest`
- Save → load roundtrip: identical object
- Clear → load: null
- Missing key → load: null

### `CaptureViewModelTest`
- `learnDevice()` → signature saved + `learnSaveConfirmation` set
- `clearLearnedDevice()` → signature null in state
- STRONG learned match → `learnedMatchResults` entry has `labelOverrideActive = true`
- NONE match → no label override
- Apple-only device explicitly learned via tap → signature saved (user intent overrides noise suppression)
