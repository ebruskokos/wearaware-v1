package com.wearaware.app.domain.model

/**
 * PURPOSE: Tracks whether a BLE device is currently being seen or has gone quiet.
 * LIMITATIONS: SIGNAL_LOST does not mean the device has left — it may be temporarily
 *   out of range or the BLE advertise interval may have lengthened.
 * NOTES: Separate from ProximityLabel intentionally — visibility is about recency,
 *   proximity is about signal strength.
 */
enum class VisibilityState {
    DETECTED_NOW,  // device seen within SIGNAL_LOST_AFTER_MS
    SIGNAL_LOST    // device not seen within SIGNAL_LOST_AFTER_MS
}
