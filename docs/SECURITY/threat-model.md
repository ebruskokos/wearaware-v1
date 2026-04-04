# WearAware Threat Model

**Version:** 1.0  
**Date:** 2026-04-03

## Assets

| Asset | Description | Sensitivity |
|---|---|---|
| Session scan log | On-device Room DB; timestamps, device fingerprints, RSSI | Low — no PII |
| BLE fingerprint rules | Bundled JSON asset | Low — public detection logic |
| User location (implicit) | Inferred from BLE scan timing | Not collected or stored |

## Non-Assets (Explicitly Not Collected)

- User identity or account information
- GPS or network location
- Device MAC addresses in long-term storage
- Cross-session device encounter history
- Any data transmitted off-device

## Threats

### T1: Sensitive data exposure via session log
**Risk:** Session log read by other apps or extracted from device.  
**Mitigation:** Room database uses Android's internal storage (not externally accessible
without root). No cloud sync. User can clear at any time.

### T2: False positive causing user alarm
**Risk:** A non-wearable device classified as camera-capable wearable.  
**Mitigation:** min_score threshold; isWearableCandidate gate; LOW-confidence matches
suppressed from alerts; non-alarmist safe wording only.

### T3: False negative — wearable not detected
**Risk:** A real wearable device is not detected.  
**Mitigation:** Documented limitation. Users informed that detection is not guaranteed.

### T4: Rule file tampering
**Risk:** Modified fingerprint_rules.json changes classification behavior.  
**Mitigation:** Rules are bundled as an app asset (not downloaded). rule_set_hash
in CHANGELOG enables integrity verification per release.

## Out of Scope

- Network-based attacks (no network access in WearAware)
- Physical device compromise (OS-level, out of scope)
- Social engineering
