package com.wearaware.app.util

/**
 * PURPOSE: Holds version information surfaced to ScanScreen via ScanUiState.
 *   Populated at app startup from BuildConfig and the loaded rule set metadata.
 * NOTES: Provided as a @Singleton via BleModule so the values are resolved once
 *   and reused everywhere they are injected.
 */
data class AboutInfo(
    /** App version name (e.g. "1.0.0"). Sourced from BuildConfig.VERSION_NAME at startup. */
    val appVersion: String,
    /** Rule set version string from fingerprint_rules.json metadata. */
    val ruleSetVersion: String,
    /** SHA-256 hash of the rule set file from fingerprint_rules.json metadata. */
    val ruleSetHash: String
)
