# ADR-001: BLE Scanning Approach

**Date:** 2026-04-03  
**Status:** Accepted

## Context

WearAware needs to scan for nearby BLE devices to detect consumer wearable devices.
Android provides three BLE scanning mechanisms: BluetoothLeScanner (API 21+),
Companion Device Manager (API 26+), and Wi-Fi/BLE Combined (not applicable).

## Decision

Use `BluetoothLeScanner` with `SCAN_MODE_LOW_LATENCY` while the app is in the foreground.

## Rationale

- BluetoothLeScanner provides direct access to manufacturer data, service UUIDs, and RSSI
  in each `ScanResult` — required for our fingerprinting approach
- SCAN_MODE_LOW_LATENCY maximizes detection responsiveness during active use
- Companion Device Manager is scoped to pairing, not ambient awareness scanning
- In-app only (no foreground service) keeps v1 simple and avoids Android background restrictions

## Alternatives Considered

- **Companion Device Manager**: Rejected — designed for pairing, not ambient BLE scanning
- **SCAN_MODE_BALANCED**: Rejected — too slow for real-time proximity awareness
- **WorkManager periodic scans**: Rejected — deferred to v1.1; adds complexity before
  fingerprinting is validated

## Limitations

- BluetoothLeScanner requires a foreground app context
- Android 6+ randomizes BLE MAC addresses; device IDs are session-scoped only
- RSSI values vary significantly with environment and device orientation
- BLUETOOTH_SCAN permission requires neverForLocation flag on API 31+ to avoid
  location permission dependency

## Consequences

- BleScanner.kt is the single class allowed to import android.bluetooth
- Scanning stops when app is backgrounded (v1.0 constraint)
- All downstream logic (smoothing, classification, alerts) is pure Kotlin
