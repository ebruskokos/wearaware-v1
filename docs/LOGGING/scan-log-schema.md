# WearAware Scan Log Schema

**Version:** 1.0  
**Date:** 2026-04-03

## Purpose

Documents the schema of the local session log (Room database table: `scan_log`).

## Schema

| Field | Type | Nullable | Description |
|---|---|---|---|
| id | Long | No | Auto-generated primary key |
| timestamp | Long | No | Epoch milliseconds when this event was logged |
| deviceId | String | No | Session-scoped fingerprint (not MAC address) |
| advertisedName | String | Yes | BLE advertised device name; null if not broadcast |
| rawRssi | Int | No | Raw RSSI reading at time of log |
| averagedRssi | Int | No | Rolling-average RSSI at time of log |
| proximityLabel | String | No | VERY_CLOSE / STRONG / NEARBY / WEAK / UNKNOWN |
| visibilityState | String | No | DETECTED_NOW / SIGNAL_LOST |
| matchedRuleId | String | Yes | ID of the matched fingerprint rule; null if no match |
| ruleVersion | String | Yes | rule_set_version used for classification |
| category | String | No | DeviceCategory enum name |
| confidence | String | No | ConfidenceLevel enum name |
| evaluationNotes | String | Yes | Human-readable match trace, e.g. "manufacturerId matched" |

## Notes

- `deviceId` is a session-scoped hash derived from stable BLE advertising fields.
  It is NOT a MAC address. The same physical device may have a different `deviceId`
  across sessions due to BLE address randomization.
- `advertisedName` may be null; many BLE devices do not broadcast a name.
- All data is stored on-device only. No data is transmitted externally.

## Retention

Session logs persist until the user taps "Clear session log" in the app.
No automatic expiry. No cross-session aggregation.

## No PII

This log does not contain: user identity, GPS location, MAC addresses,
or any data that identifies a natural person.
