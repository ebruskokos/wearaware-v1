# Changelog

All notable changes to WearAware are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- Initial project scaffolding
- PRD v1.0
- Documentation structure (ADR, SECURITY, COMPLIANCE, LOGGING, TESTING, DECISIONS)

---

## [1.0.0] — Unreleased

### Added
- BLE scanning pipeline (in-app only)
- Config-driven fingerprint rule engine (fingerprint_rules.json v1.0.0)
- RSSI smoothing (rolling average, window=5)
- Proximity labels: VERY_CLOSE, STRONG, NEARBY, WEAK, UNKNOWN
- Signal bars UI (1–5 bars, color-coded)
- Device list with sort-by-signal
- Persistence alert system (60s threshold, NEARBY+ proximity gate)
- Local session log (Room, on-device, user-clearable)
- Safe wording — no recording claims, no following claims
- SOC2-aligned documentation

### Rule Set
- rule_set_version: 1.0.0
- Initial rules: meta_rayban_v1, snapchat_spectacles_v1, generic_smartwatch_v1
