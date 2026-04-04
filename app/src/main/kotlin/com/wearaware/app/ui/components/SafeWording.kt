package com.wearaware.app.ui.components

/**
 * PURPOSE: Centralized repository for all user-facing strings that describe
 *   device detections and alerts. Every alert message in the app must come
 *   from here — never from free-form strings in ViewModels, use cases, or screens.
 * LIMITATIONS: Safe wording is intentionally vague. WearAware cannot determine
 *   whether a device is actually recording or whether a person is following anyone.
 * NOTES: Matches the safe wording requirements in PRD v1.0 Section 3.8.
 *   If wording changes, update this file AND docs/PRD (bump PRD version) AND CHANGELOG.
 */
object SafeWording {
    const val DISCLAIMER =
        "Proximity is estimated from Bluetooth signal strength and may vary based on " +
        "physical obstructions, device orientation, and interference."

    const val RECORDING_UNKNOWN =
        "Recording activity cannot be determined."

    const val DEVICE_REMAINED =
        "This device has remained near you."

    const val CAMERA_CAPABLE =
        "Camera-capable wearable detected nearby."

    const val UNKNOWN_DEVICE =
        "Unknown BLE device."
}
