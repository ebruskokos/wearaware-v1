# WearAware — Meta Ray-Ban Glasses Test Plan

**Version:** 1.0  
**Date:** 2026-04-03  
**Target Device:** Ray-Ban Meta Smart Glasses (1st or 2nd gen)

## Prerequisites

- WearAware installed on Android test device (API 26+)
- Meta Ray-Ban glasses with Bluetooth enabled
- Glasses are not connected to any other phone during test (to ensure BLE advertising is active)
- Open area or standard indoor environment

## Expected RSSI Ranges (baseline)

| Distance (approx.) | Expected averaged RSSI |
|---|---|
| 0.5–1 m | -45 to -55 dBm |
| 1–2 m | -55 to -65 dBm |
| 2–3 m | -65 to -75 dBm |
| 3–5 m | -75 to -85 dBm |
| 5+ m | below -85 dBm |

Note: values vary significantly based on obstacles, reflections, and orientation.

## Test Scenarios

### T1: Basic Detection
1. Start WearAware scanning
2. Hold glasses 1–2 m from phone
3. **Expected:** Device appears in list within 5 seconds
4. **Expected:** Label shows "Camera-capable wearable detected nearby"
5. **Expected:** Confidence shown as HIGH

### T2: Signal Bars and Proximity
1. Start scanning with glasses at 0.5 m
2. Move glasses to 2 m, then 4 m, then >5 m
3. **Expected:** Signal bars decrease as distance increases
4. **Expected:** Proximity label changes from VERY_CLOSE → STRONG → NEARBY → WEAK → UNKNOWN

### T3: RSSI Smoothing
1. Start scanning with glasses nearby
2. Rapidly move glasses away and back
3. **Expected:** Signal bars change smoothly, not jittery
4. **Expected:** Averaging damps single-reading spikes

### T4: Signal Lost Behavior
1. Start scanning with glasses nearby (DETECTED)
2. Move glasses >10 m away or turn off BLE
3. **Wait 15 seconds**
4. **Expected:** Device shows "Signal lost" badge (grey dot)
5. **Wait 30 more seconds**
6. **Expected:** Device removed from list

### T5: Persistence Alert
1. Start scanning with glasses at 1–3 m
2. Wait 60+ seconds without moving glasses far away
3. **Expected:** Alert banner appears at top of ScanScreen
4. **Expected:** Banner text uses safe wording only (no "following", no "recording")
5. **Expected:** Banner shows device name and duration
6. Tap Dismiss
7. **Expected:** Banner disappears; device still in list

### T6: Alert Cooldown
1. Trigger persistence alert (T5)
2. Move glasses out of range, wait for Signal Lost
3. Bring glasses back for 60+ seconds
4. **Expected:** No new alert until 120s cooldown from first alert has passed

### T7: Session Log
1. Complete T1–T5
2. Navigate to Session Log
3. **Expected:** Log shows entries for each detection event
4. **Expected:** Each entry shows matched rule ID (meta_rayban_v1) and rule version
5. Tap "Clear session log"
6. **Expected:** Log is empty after confirmation

## Pass Criteria

- [ ] T1: Device detected and correctly classified
- [ ] T2: Proximity labels transition correctly with distance
- [ ] T3: Signal bars are smooth (no rapid jumping)
- [ ] T4: Signal lost and removal timing matches config
- [ ] T5: Persistence alert triggers and uses safe wording
- [ ] T6: Cooldown prevents re-alert within 120s
- [ ] T7: Session log records and clears correctly
