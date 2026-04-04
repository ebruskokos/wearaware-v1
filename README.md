# WearAware

A consumer privacy awareness Android app that scans nearby Bluetooth Low Energy (BLE) devices,
detects wearable devices such as smart glasses, and provides calm, non-accusatory alerts.

**Version:** 1.0.0 | **Rule Set:** 1.0.0

---

## Overview

WearAware helps you become aware of camera-capable wearable devices nearby.
It does NOT claim anyone is recording you. It does NOT claim anyone is following you.
It provides proximity estimates and awareness — nothing more.

> Proximity is estimated from Bluetooth signal strength and may vary based on physical
> obstructions, device orientation, and interference. Recording activity cannot be determined.

---

## Implemented Features (v1.0)

- [x] BLE scanning while app is open
- [x] Config-driven fingerprint rule engine (fingerprint_rules.json)
- [x] RSSI smoothing (rolling average, configurable window)
- [x] Proximity labels: Very close / Strong / Nearby / Weak / Unknown
- [x] 1–5 signal bars (color-coded, no green "safe" color)
- [x] Device list sorted by strongest signal
- [x] Persistence alert banner (60s sustained presence + NEARBY or closer)
- [x] Alert suppression for low-confidence matches
- [x] Local session log (on-device, user-clearable)
- [x] Device detail screen with classification explanation
- [x] Safe wording centralized in `SafeWording` object

---

## Not Implemented (v1.0)

- [ ] Background scanning (planned v1.1)
- [ ] Cross-session device history (planned v2.0)
- [ ] Radar / spatial visualization
- [ ] Industrial or enterprise device detection
- [ ] Cloud sync or remote logging

---

## Known Limitations

- Device identifiers are session-scoped due to BLE MAC address randomization (Android 6+).
  The same physical device may appear with a different ID across app sessions.
- Proximity is estimated, not measured. BLE RSSI varies significantly with environment.
- False positives (non-wearable devices triggering detection) and false negatives
  (wearables not detected) are possible and expected.
- Recording activity cannot be determined from BLE signals.
- Scanning requires the app to be open (v1.0). Background scanning is planned for v1.1.

---

## How It Works

1. App opens BLE scanner using `BluetoothLeScanner` API
2. Each scan result is parsed for manufacturer data, service UUIDs, and device name
3. RSSI readings are smoothed using a rolling average (window = 5)
4. Device is classified against `fingerprint_rules.json` using a priority-scored rule engine
5. Matched rule ID and rule version are recorded for auditability
6. Proximity label is assigned from averaged RSSI thresholds
7. If a wearable candidate has been continuously DETECTED_NOW for 60+ seconds at
   NEARBY proximity or closer, an in-app alert banner is shown
8. All detections are logged locally to a session log (Room database)

---

## Testing With Meta Ray-Ban Glasses

See `docs/TESTING/meta-glasses-test-plan.md` for full test scenarios.

Quick start:
1. Enable Bluetooth on your Android device
2. Put on Meta Ray-Ban glasses and ensure Bluetooth is active on them
3. Open WearAware and tap **Start Scanning**
4. Walk within ~5 meters of the glasses
5. The device should appear labeled "Camera-capable wearable detected nearby"
6. Stay nearby for 60+ seconds to trigger the persistence alert banner

Expected RSSI range (Meta Ray-Ban): approximately -50 to -75 dBm at 1–3 meters.

---

## Architecture

Clean Architecture: UI → Domain → Data

- **Domain layer** — pure Kotlin, zero Android dependencies
- **Data layer** — BLE scanner, Room database, rules loader
- **UI layer** — Jetpack Compose, MVVM, StateFlow

See `docs/ADR/ADR-001-ble-scanning.md` for architectural decisions.

---

## Next Steps

After v1.0 validation with Meta glasses:
- Tune RSSI thresholds in `fingerprint_rules.json`
- Add additional consumer device rules
- Implement foreground service for background scanning (v1.1)
