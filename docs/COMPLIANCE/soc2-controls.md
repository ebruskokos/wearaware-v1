# WearAware SOC2 Controls Reference

**Version:** 1.0  
**Date:** 2026-04-03

## No PII Declaration

> WearAware does not collect, store, or transmit personally identifiable information (PII).
> All data is processed locally on-device and is session-scoped only.

## Data Minimization

| Data Type | Collected | Stored | Transmitted | Notes |
|---|---|---|---|---|
| BLE device name | Yes (from BLE API) | Session log only | Never | May be null |
| BLE MAC address | Read transiently | Never stored directly | Never | Session fingerprint derived instead |
| RSSI readings | Yes | Session log (averaged) | Never | Signal strength only |
| User location | Never | Never | Never | Not collected |
| User identity | Never | Never | Never | No accounts |

## Audit Trail Controls

| Control | Implementation |
|---|---|
| Change tracking | CHANGELOG.md — every detection behavior change logged |
| Rule versioning | rule_set_version + rule_set_hash in CHANGELOG per release |
| Classification traceability | matchedRuleId + ruleVersion stored in every ScanLogEntry |
| PRD versioning | docs/PRD/v{N}.md + CURRENT.md — approval table per version |
| ADR | docs/ADR/ — architectural decisions recorded |

## Access Controls

- Session log stored in Android internal storage (app-private by default)
- No external API access, no cloud storage, no account system
- User has full control: can clear session log at any time via app UI

## Retention Policy

- Session logs retained locally until user clears them
- No automatic expiry in v1.0
- No cross-session aggregation
- Data does not leave the device

## Known Limitations (Required Disclosures)

- Device identifiers are session-scoped; not stable across app sessions
- Proximity is estimated; not measured with precision
- Detection coverage is not guaranteed; false positives and negatives are possible
- Recording activity cannot be determined from BLE signals

## Classification Integrity

Classification is deterministic: given the same scan input and rule_set_version,
the output is always identical. This ensures reproducibility for any audit review.
