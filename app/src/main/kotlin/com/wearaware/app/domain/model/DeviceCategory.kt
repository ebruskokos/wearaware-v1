package com.wearaware.app.domain.model

/**
 * PURPOSE: Classification category assigned by the fingerprint rule engine.
 * LIMITATIONS: Categories are based on BLE advertising data only — actual device
 *   capabilities cannot be confirmed from BLE signals.
 * NOTES: UNKNOWN_BLE_DEVICE is the safe fallback when no rule matches.
 */
enum class DeviceCategory {
    CAMERA_CAPABLE_WEARABLE,           // matched a known camera-capable device rule
    SMART_GLASSES,                      // matched a smart glasses rule without camera certainty
    SMARTWATCH,                         // matched a smartwatch / fitness band rule
    UNCLASSIFIED_WEARABLE_CANDIDATE,    // weak heuristic match, cannot confirm wearable
    UNKNOWN_BLE_DEVICE                  // no rule matched
}
