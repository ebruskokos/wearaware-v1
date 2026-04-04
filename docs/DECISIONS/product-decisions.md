# WearAware — Product Decisions Log

**Version:** 1.0  
**Date:** 2026-04-03

## Decision 1: In-App Scanning Only (v1.0)

**Decision:** WearAware v1.0 scans only while the app is open. No background scanning.

**Why:** We want to validate BLE detection quality, RSSI behavior, and fingerprinting
accuracy with Meta glasses before adding background infrastructure complexity.
Background scanning requires a Foreground Service, adds battery considerations,
and increases Android permission complexity.

**Future:** Foreground Service background scanning planned for v1.1.

---

## Decision 2: Consumer Wearables Only

**Decision:** v1.0 targets consumer smart glasses and smartwatches only.
Industrial, enterprise, and law enforcement devices are out of scope.

**Why:** Consumer devices have predictable BLE patterns and known manufacturer IDs.
We have Meta Ray-Ban glasses for real-world testing. Enterprise devices are
inconsistent and harder to fingerprint without representative hardware.

**Future:** Expand to additional device classes after consumer fingerprinting is validated.

---

## Decision 3: No Green "Safe" Color

**Decision:** Signal bar colors do not include green. Colors go grey → yellow → orange → red.

**Why:** Green implies a device is "safe" or benign. WearAware cannot determine
intent or safety. A strong signal (red = very close) indicates proximity only.
Using red for proximity does not mean danger — it means the device is nearby.

---

## Decision 4: Config-Driven Rule Engine

**Decision:** Device classification rules live in `fingerprint_rules.json` (bundled asset),
not hardcoded in application logic.

**Why:** Allows rule updates without app logic changes. Keeps classification auditable
and versionable. Easier to tune thresholds after real-world testing.

---

## Decision 5: Rolling Average RSSI Smoothing (Not EMA)

**Decision:** v1.0 uses a simple rolling average for RSSI smoothing.

**Why:** Simpler to implement and tune. Easier to reason about and document.
Good enough for v1 validation with Meta glasses.

**Future:** Exponential Moving Average (EMA) may replace this in v2.0 for
smoother signal transitions and better responsiveness to rapid changes.

---

## Decision 6: Session-Scoped Device IDs

**Decision:** Device identifiers are derived from BLE advertising data (manufacturer ID,
name, service UUIDs) hashed into a session fingerprint. MAC addresses are not stored.

**Why:** Android 6+ randomizes BLE MAC addresses, making them unreliable as identifiers.
Avoiding MAC storage reduces privacy risk. Session-scoped IDs are sufficient for
the v1.0 use case of real-time awareness.
