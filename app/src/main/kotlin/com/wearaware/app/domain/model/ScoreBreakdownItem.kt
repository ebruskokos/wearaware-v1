package com.wearaware.app.domain.model

/**
 * One line in the weighted match score breakdown for a [KnownTargetMatchResult].
 * Positive [points] = bonus; negative = penalty.
 */
data class ScoreBreakdownItem(
    val category: ScoreCategory,
    val points: Int,
    val description: String
)

enum class ScoreCategory {
    MANUFACTURER_ID,
    PREFIX_MATCH,
    SERVICE_UUID,
    GATT_UUID,
    FINGERPRINT_ID,
    PROXIMITY,
    PERSISTENCE,
    CONNECTABLE,
    VISIBLE_AT_STOP,
    APPLE_PENALTY,
    WEAK_SIGNAL,
    LOW_PERSISTENCE
}
