package com.wearaware.app.domain.model

/**
 * PURPOSE: Debug record of the richest evidence observed during a single capture window.
 *   Populated by DeviceAccumulator during live capture; null when loaded from Room
 *   (session data loaded from DB is complete but does not carry this ephemeral log).
 *
 * NOTES: All fields reflect the full capture window, not just the final state.
 *   Use this to diagnose whether the glasses exposed richer advertising data on later ticks
 *   (e.g. name appearing only after a few seconds, manufacturerIds populated mid-scan).
 */
data class CaptureObservationLog(
    /**
     * All distinct advertised names seen during the capture window, in observation order.
     * Useful for detecting intermittent name broadcasting.
     */
    val observedNames: List<String>,

    /**
     * Best (richest) set of manufacturer IDs observed. Equivalent to the final
     * non-empty manufacturerIds set seen — reflects whether richer data appeared later.
     */
    val bestManufacturerIds: List<Int>,

    /**
     * Best manufacturer data summary observed. e.g. "0075:deadbeef".
     */
    val bestManufacturerDataSummary: String?,

    /**
     * Classification history — all distinct DeviceCategory values observed, in order.
     * Single element if classification never changed. Shows whether classification upgraded
     * from UNKNOWN_BLE_DEVICE to a more specific category mid-scan.
     */
    val classificationHistory: List<DeviceCategory>,

    /**
     * All distinct target match scores seen during capture, in ascending order.
     * Only new (higher) values are recorded. Empty if no match occurred.
     */
    val targetMatchScoreHistory: List<Int>
)
