package com.wearaware.app.domain.model

/**
 * PURPOSE: Represents estimated proximity of a BLE device based on averaged RSSI.
 * LIMITATIONS: Estimates only. RSSI varies with environment, obstacles, and device orientation.
 * NOTES: Thresholds are defined in ProximityConfig. Do not add logic here.
 */
enum class ProximityLabel {
    VERY_CLOSE,  // averagedRssi >= -55 dBm
    STRONG,      // averagedRssi >= -65 dBm
    NEARBY,      // averagedRssi >= -75 dBm
    WEAK,        // averagedRssi >= -85 dBm
    UNKNOWN      // averagedRssi < -85 dBm OR device not recently seen
}
