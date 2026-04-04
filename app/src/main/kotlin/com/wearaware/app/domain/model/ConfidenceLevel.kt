package com.wearaware.app.domain.model

/**
 * PURPOSE: Indicates how confident the rule engine is in a classification.
 * LIMITATIONS: Confidence is rule-defined, not statistically derived.
 * NOTES: LOW confidence matches are shown in the device list but never trigger alerts.
 */
enum class ConfidenceLevel {
    HIGH,    // strong signal match (e.g. manufacturer ID confirmed)
    MEDIUM,  // partial match (e.g. name pattern only, no manufacturer ID)
    LOW      // weak heuristic; treat as informational only
}
