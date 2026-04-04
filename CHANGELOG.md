# Changelog

All notable changes to WearAware are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.0] - 2026-04-03

### Added
- BLE scanner with rolling RSSI smoothing (window=5)
- Config-driven fingerprint rule engine (fingerprint_rules.json v1.0.0)
- Three fingerprint rules: Ray-Ban Meta, Snapchat Spectacles, generic smartwatch
- Persistence alert system (60s threshold, 120s cooldown, 6-gate evaluation)
- Session log with Room database (local only, no PII)
- Clean Architecture: UI → Domain → Data (pure Kotlin domain layer)
- SafeWording centralization — all alert/disclaimer strings in one object
- Accessibility: signal bars with content descriptions, color not sole indicator
- Hilt dependency injection throughout

### Rule Set
- rule_set_version: 1.0.0
- rule_set_hash: sha256:placeholder-update-after-file-is-finalized — update with actual hash from fingerprint_rules.json

### Known Limitations
- Gradle wrapper JAR must be generated before building: `gradle wrapper --gradle-version 8.2`
- Device identifiers are session-scoped (BLE MAC randomization)
- Proximity is estimated, not measured
- Physical Meta Ray-Ban testing pending (see docs/TESTING/meta-glasses-test-plan.md)
- Unit tests: 48 tests across 6 files (ProximityConfig×8, RssiSmoother×8, ObservedDevice×3, FingerprintClassifier×11, ScanLogMapper×4, EvaluatePersistence×14)
