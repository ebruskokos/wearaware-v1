# WearAware Permissions Policy

**Version:** 1.0  
**Date:** 2026-04-03

## Permissions Requested

### Android 12+ (API 31+)

| Permission | Reason | Flag |
|---|---|---|
| BLUETOOTH_SCAN | Required to discover nearby BLE devices | neverForLocation |
| BLUETOOTH_CONNECT | Required to read advertised device names | — |

The `neverForLocation` flag declares that WearAware does NOT use BLE scanning
to derive location. This avoids requiring ACCESS_FINE_LOCATION on API 31+.

### Android 6–11 (API 23–30)

| Permission | Reason |
|---|---|
| ACCESS_FINE_LOCATION | Required by Android OS for BLE scanning on these API levels |
| ACCESS_COARSE_LOCATION | Required alongside FINE_LOCATION |

Location permission on API 23–30 is an OS requirement for BLE scanning, not because
WearAware uses or stores location data. No location data is collected or stored.

## Permissions NOT Requested

- RECORD_AUDIO — WearAware does not record audio
- CAMERA — WearAware does not use the camera
- ACCESS_BACKGROUND_LOCATION — not required in v1.0 (in-app scanning only)
- INTERNET — WearAware has no network functionality

## Runtime Permission Flow

1. User taps "Start Scanning"
2. App checks if required BLE permissions are granted
3. If not granted: launches OS permission dialog
4. If granted: starts BLE scanning
5. If denied: shows "Permissions required to scan" state

## Rationale for Each Permission

All permissions are the minimum required for core functionality. No permissions are
requested speculatively or for future features.
