# WearAware v1.0 — Design Specification

**Date:** 2026-04-03  
**Status:** Approved  
**Author:** Design session (Claude Code + user)  
**Scope:** MVP — Android app, consumer wearables, in-app scanning only

---

## 1. Product Overview

WearAware is a consumer privacy awareness Android app that scans nearby Bluetooth Low Energy (BLE) devices, detects consumer wearable devices (primarily camera-capable smart glasses), shows signal strength, estimates proximity, and provides calm, non-accusatory in-app alerts when a wearable device has been nearby for a sustained period.

### Critical product rules (non-negotiable)
- DO NOT claim someone is recording the user
- DO NOT claim someone is following the user
- DO NOT show exact distance
- ALWAYS include disclaimers
- ONLY use safe wording (centralized in `SafeWording` object)

### Safe wording examples
- "Camera-capable wearable detected nearby"
- "This device has remained near you"
- "Recording activity cannot be determined"
- "Proximity is estimated from Bluetooth signal strength and may vary based on surroundings"

---

## 2. Scope — v1 MVP

### In scope
- Consumer wearable devices only:
  - Ray-Ban Meta smart glasses (primary test target)
  - Snapchat Spectacles
  - Generic Bluetooth-enabled smart glasses
  - Smartwatches (secondary, lower priority)
- In-app scanning only (app must be open)
- Local on-device session log (review + clear)
- Config-driven fingerprint rule engine

### Out of scope for v1
- Background scanning / foreground service
- WorkManager periodic scans
- Cross-session device intelligence
- Industrial, enterprise, or law enforcement wearables
- Cloud sync or remote logging
- Radar / spatial visualization UI
- AI-based classification

### Future phases
- v1.1: foreground service background scanning
- v2: cross-session persistence tracking, smarter heuristics
- v3: probabilistic classification, enterprise device support

---

## 3. Architecture

### Pattern: Clean Architecture with Use Cases

Three strict layers:

```
UI Layer          → Jetpack Compose screens + ViewModels
Domain Layer      → Pure Kotlin: use cases, models, rules (zero Android dependencies)
Data Layer        → BLE scanning, Room database, rules loader
```

### Data flow

```
BluetoothLeScanner (Android API)
    │
    ▼
BleScanner (data.ble)
    │  emits Flow<RawScanResult>
    ▼
BleRepository (data.repository)
    │  parses BLE payload, smooths RSSI, maps to domain model
    ▼
Domain Use Cases (domain.usecase)
    │  classify, evaluate persistence, log
    ▼
ScanViewModel (ui.viewmodel)
    │  combines all state, exposes UiState via StateFlow
    ▼
Compose Screens
    │  ScanScreen, DeviceDetailScreen, SessionLogScreen
```

### Strict architectural rules
1. `BleScanner` is the ONLY class that imports `android.bluetooth`
2. Domain models are plain Kotlin data classes — zero Android dependencies
3. ViewModels call use cases only — no BLE parsing, no rule logic
4. `BleRepository` may smooth and normalize scan data, but business meaning (classification, alert eligibility, "camera-capable wearable") is decided in the domain layer only
5. All safe wording strings live in `ui/components/SafeWording.kt` — never free-form in ViewModels or use cases

### Package structure

```
com.wearaware.app/
├── ui/
│   ├── screens/          ScanScreen, DeviceDetailScreen, SessionLogScreen
│   ├── components/       DeviceCard, SignalBars, AlertBanner, SafeWording
│   ├── navigation/       NavGraph
│   ├── viewmodel/        ScanViewModel
│   └── theme/            MaterialTheme, colors, typography
├── domain/
│   ├── model/            ObservedDevice, ClassificationResult, PersistenceAlert, etc.
│   ├── usecase/          all use cases
│   ├── repository/       interfaces only
│   └── rules/            FingerprintClassifier, FingerprintRule, ProximityConfig
├── data/
│   ├── ble/              BleScanner, RawScanResult
│   ├── local/            Room DB, ScanLogDao, ScanLogEntity
│   ├── mapper/           entity↔domain mappers
│   ├── repository/       implementations of domain repository interfaces
│   └── rules/            RulesLoader (loads + parses fingerprint_rules.json)
└── util/                 FormatUtils, TimeUtils
```

---

## 4. Domain Models

All models are pure Kotlin — zero Android imports.

### ObservedDevice
```kotlin
data class ObservedDevice(
    val id: String,                         // session-scoped fingerprint (not MAC)
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,                  // rolling average
    val proximityLabel: ProximityLabel,
    val visibilityState: VisibilityState,
    val firstSeenAt: Long,                  // epoch ms
    val lastSeenAt: Long,
    val seenCount: Int,
    val classification: ClassificationResult,
    val persistenceAlert: PersistenceAlert?
) {
    val seenDurationMs: Long get() = lastSeenAt - firstSeenAt
}
```

**Note on device ID:** Android 6+ randomises BLE MAC addresses. `id` is derived from a combination of stable advertising fields (manufacturer data bytes, service UUIDs, advertised name) hashed into a session fingerprint. The same physical device may receive a different `id` across sessions if it rotates its address. This is documented as a known limitation.

### ProximityLabel + VisibilityState
```kotlin
enum class ProximityLabel { VERY_CLOSE, STRONG, NEARBY, WEAK, UNKNOWN }
enum class VisibilityState { DETECTED_NOW, SIGNAL_LOST }
```

### ClassificationResult
```kotlin
data class ClassificationResult(
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: DeviceCategory,
    val displayLabel: String,
    val confidence: ConfidenceLevel,
    val isWearableCandidate: Boolean,
    val evaluationNotes: String?            // e.g. "manufacturerId=0x0075 matched"
)

enum class DeviceCategory {
    CAMERA_CAPABLE_WEARABLE,
    SMART_GLASSES,
    SMARTWATCH,
    UNCLASSIFIED_WEARABLE_CANDIDATE,
    UNKNOWN_BLE_DEVICE
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
```

### PersistenceAlert
```kotlin
data class PersistenceAlert(
    val deviceId: String,
    val deviceDisplayLabel: String,
    val durationMs: Long,
    val alertType: PersistenceAlertType,
    val triggeredAt: Long
)

enum class PersistenceAlertType {
    DEVICE_REMAINED_NEARBY    // only type in v1
}
```

### ScanLogEntry (Room entity)
```kotlin
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
    val evaluationNotes: String?
)
```

---

## 5. Use Cases

All located in `domain/usecase/`. All are pure Kotlin.

| Use Case | Input | Output | Purpose |
|---|---|---|---|
| `ObserveScannedDevicesUseCase` | — | `Flow<List<ObservedDevice>>` | Live enriched device stream |
| `ClassifyDeviceUseCase` | `RawScanResult` | `ClassificationResult` | Apply fingerprint rules |
| `EvaluatePersistenceUseCase` | `ObservedDevice`, config, last alert map | `PersistenceAlert?` | Check alert conditions |
| `LogScanEventUseCase` | `ObservedDevice` | `Unit` | Write to Room |
| `GetSessionLogUseCase` | — | `Flow<List<ScanLogEntry>>` | Read session log |
| `ClearSessionLogUseCase` | — | `Unit` | Delete all session log rows |

---

## 6. Fingerprint Rule Engine

### Rule file: `assets/fingerprint_rules.json`

Bundled with the app. Versioned independently from app code. Format:

```json
{
  "rule_set_version": "1.0.0",
  "rule_set_hash": "sha256:<hash>",
  "rules": [
    {
      "rule_id": "meta_rayban_v1",
      "enabled": true,
      "priority": 100,
      "min_score": 4,
      "manufacturer_ids": ["0x0075"],
      "name_patterns": ["Ray-Ban", "Meta", "Aria"],
      "service_uuids": [],
      "device_type_hint": "smart_glasses",
      "category": "CAMERA_CAPABLE_WEARABLE",
      "display_label": "Camera-capable wearable detected nearby",
      "confidence": "HIGH",
      "is_wearable_candidate": true
    }
  ]
}
```

### Scoring algorithm (deterministic)

```
1. Normalize input: lowercase name, trim whitespace, normalize UUIDs and manufacturer IDs
2. Load enabled rules sorted by priority DESC
3. For each rule, compute score:
   - manufacturerId match  → +4 points
   - name pattern match   → +2 points
   - serviceUUID match    → +2 points
4. Find best candidate: highest score ≥ rule.min_score
   - Tie: higher priority wins
5. If no rule meets min_score → UNKNOWN_BLE_DEVICE
6. Return ClassificationResult with matchedRuleId, ruleVersion, category,
   displayLabel, confidence, isWearableCandidate, evaluationNotes
```

**Classification must be deterministic:** Given the same scan input and rule_set_version, the output must always be identical. This is required for reproducibility and audit traceability.

### Fallback
Any device with no rule match:
- category: `UNKNOWN_BLE_DEVICE`
- displayLabel: "Unknown BLE device"
- confidence: `LOW`
- isWearableCandidate: `false`

Low-confidence matches appear in the device list but NEVER trigger the alert banner.

---

## 7. RSSI Smoothing & Proximity Logic

### RssiSmoother
Rolling average over a configurable window (default 5 readings). Lives in `domain/rules/`.

### ProximityConfig (single source of truth)
```kotlin
object ProximityConfig {
    const val VERY_CLOSE_THRESHOLD_DBM = -55
    const val STRONG_THRESHOLD_DBM     = -65
    const val NEARBY_THRESHOLD_DBM     = -75
    const val WEAK_THRESHOLD_DBM       = -85
    const val SIGNAL_LOST_AFTER_MS     = 15_000L
    const val REMOVE_AFTER_MS          = 30_000L
    const val RSSI_WINDOW_SIZE         = 5
}
```

### Proximity thresholds
```
averagedRssi ≥ -55 dBm  → VERY_CLOSE
averagedRssi ≥ -65 dBm  → STRONG
averagedRssi ≥ -75 dBm  → NEARBY
averagedRssi ≥ -85 dBm  → WEAK
below -85 dBm (active)  → UNKNOWN
```

### Visibility state
- `DETECTED_NOW`: device seen within last `SIGNAL_LOST_AFTER_MS`
- `SIGNAL_LOST`: not seen recently; last known signal shown as stale. No alert evaluation while lost.
- Device removed from list after `REMOVE_AFTER_MS` in `SIGNAL_LOST` state.

### Signal bars (UI)
```
VERY_CLOSE → 5 bars
STRONG     → 4 bars
NEARBY     → 3 bars
WEAK       → 2 bars
UNKNOWN    → 1 bar (grey)
```

Signal bar colors: grey (unknown/weak) → yellow (nearby) → orange (strong) → red (very close). No green — avoids implying "safe."

**Future:** Rolling average may be replaced by exponential moving average for smoother transitions.

---

## 8. Persistence Alert Logic

### Conditions (all must be true to trigger)
1. `visibilityState == DETECTED_NOW`
2. `seenDurationMs >= alertThresholdMs` (default 60,000 ms)
3. `classification.isWearableCandidate == true`
4. `classification.confidence >= MEDIUM`
5. `proximityLabel` is `NEARBY`, `STRONG`, or `VERY_CLOSE` (not `WEAK` or `UNKNOWN`)
6. No active cooldown for `deviceId + alertType` key (cooldown default 120,000 ms)

### Continuous presence
`seenDurationMs` resets when the device enters `SIGNAL_LOST` for longer than a brief grace period. Brief flickers (< `SIGNAL_LOST_AFTER_MS`) do not reset duration.

### Alert behavior
- One active banner at a time; most recently triggered replaces previous
- Device entering `SIGNAL_LOST` immediately clears active banner
- Dismiss = hide banner only; does NOT reset cooldown
- Session alert history preserved on `DeviceDetailScreen`

### Alert suppression rule
Low-confidence (`LOW`) matches are shown in the device list but NEVER trigger the persistence alert. This is a trust feature documented in PRD and README.

---

## 9. UI Design

### Screens
- `ScanScreen` — primary screen; header + alert banner + device list + scan control
- `DeviceDetailScreen` — full device info, classification explanation, alert history
- `SessionLogScreen` — chronological log, clear button with confirmation

### DeviceCard fields
- Advertised name (or "Unknown Device")
- Signal bars (1–5, color-coded per proximity)
- ProximityLabel text
- Category display label
- `seenDurationMs` formatted ("Xm Ys")
- VisibilityState icon badge (green dot / grey dot)
- Full card is tappable (ripple + chevron)

### Scan states
- "Scanning nearby devices…"
- "Scanning stopped"
- "Bluetooth unavailable"
- "Permissions required"
- Empty state: "No nearby devices detected"

### SafeWording (centralized)
```kotlin
object SafeWording {
    const val DISCLAIMER = "Proximity is estimated from Bluetooth signal strength " +
        "and may vary based on physical obstructions, device orientation, and interference."
    const val RECORDING_UNKNOWN = "Recording activity cannot be determined."
    const val DEVICE_REMAINED = "This device has remained near you."
    const val CAMERA_CAPABLE = "Camera-capable wearable detected nearby."
    const val UNKNOWN_DEVICE = "Unknown BLE device."
}
```

### Accessibility
- Signal bars must have text equivalents (content description)
- Color must not be the only visual indicator of status

---

## 10. Session Logging

### What is logged (per detection event)
- timestamp
- deviceId (session fingerprint)
- advertisedName (nullable)
- rawRssi
- averagedRssi
- proximityLabel
- visibilityState
- matchedRuleId
- ruleVersion
- category
- confidence
- evaluationNotes

### Retention policy
- Session logs are on-device only
- User can clear at any time via `SessionLogScreen`
- No automatic cross-session retention in v1
- No PII stored: device identifiers are session-scoped fingerprints, not MAC addresses

---

## 11. Documentation Structure

```
mobile-app/
├── VERSION
├── README.md
├── CHANGELOG.md
├── ROADMAP.md
├── docs/
│   ├── PRD/
│   │   ├── v1.0.md
│   │   └── CURRENT.md
│   ├── ADR/
│   │   └── ADR-001-ble-scanning.md
│   ├── SECURITY/
│   │   ├── threat-model.md
│   │   └── permissions-policy.md
│   ├── COMPLIANCE/
│   │   └── soc2-controls.md
│   ├── LOGGING/
│   │   └── scan-log-schema.md
│   ├── TESTING/
│   │   └── meta-glasses-test-plan.md
│   └── DECISIONS/
│       └── product-decisions.md
└── docs/superpowers/specs/
    └── 2026-04-03-wearaware-design.md
```

### Version linkage rule
Each release must record in CHANGELOG:
- app `VERSION`
- `rule_set_version`
- `rule_set_hash`

App must expose `VERSION` at runtime (debug info or logs).

### No PII declaration
> WearAware does not collect, store, or transmit personally identifiable information (PII). All data is processed locally on-device and is session-scoped only.

### Known limitations (required disclosures)
- Device identifiers are not guaranteed stable across sessions due to BLE address randomization
- Proximity is estimated, not measured
- Recording activity cannot be determined
- False positives and missed detections are possible and expected
- BLE signal strength varies significantly based on environment and device orientation

---

## 12. Testing

### Unit tests (pure domain — no Android mocks needed)
- `FingerprintClassifier` — rule matching, scoring, normalization, determinism
- `RssiSmoother` — rolling average, window behavior
- `EvaluatePersistenceUseCase` — threshold, cooldown, proximity gate, signal-lost gate
- `ProximityConfig` — label assignment from RSSI values
- Mappers — entity↔domain round-trips

### Manual testing (Meta glasses)
See `docs/TESTING/meta-glasses-test-plan.md` for:
- Distance test scenarios
- RSSI range validation
- Smoothing behavior validation
- Persistence alert trigger validation
- Reconnect / signal-loss behavior

---

## 13. Out of Scope Decisions

| Decision | Rationale |
|---|---|
| In-app only (no background) | Validate detection quality before adding background infrastructure |
| Consumer devices only | Predictable BLE patterns; Meta glasses available for testing |
| No cross-session intelligence | Avoids retention policy complexity and premature product claims |
| No radar UI | Spatial precision would be misleading given BLE variability |
| No green "safe" color | Avoids implying device is benign |
| Rolling average (not EMA) | Simpler, easier to tune; EMA documented as future path |
