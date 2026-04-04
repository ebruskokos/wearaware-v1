package com.wearaware.app.util

import com.wearaware.app.domain.model.ProximityLabel
import java.text.SimpleDateFormat
import java.util.*

/**
 * PURPOSE: UI display formatting helpers. Converts raw values to human-readable strings.
 * NOTES: These are UI utilities — they live in util, not in domain.
 *   ProximityLabel.displayName() is defined here as an extension for UI use only.
 */

/** Formats a duration in milliseconds to a human-readable "Xm Ys" or "Xs" string. */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/** Formats an epoch millisecond timestamp to a "HH:mm:ss" time string. */
fun Long.formatTimestamp(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))

/** Formats an epoch millisecond timestamp to a "yyyy-MM-dd HH:mm:ss" string. */
fun Long.formatFullTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))

/** Returns the human-readable display name for a ProximityLabel. */
fun ProximityLabel.displayName(): String = when (this) {
    ProximityLabel.VERY_CLOSE -> "Very close"
    ProximityLabel.STRONG     -> "Strong"
    ProximityLabel.NEARBY     -> "Nearby"
    ProximityLabel.WEAK       -> "Weak"
    ProximityLabel.UNKNOWN    -> "Unknown"
}
