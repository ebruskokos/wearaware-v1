package com.wearaware.app.domain.model

/**
 * PURPOSE: Typed enum for persistence alert types, enabling per-type cooldown keys
 *   and future extensibility without free-form strings.
 * NOTES: v1.0 has only one type. Additional types (e.g. REAPPEARED_AFTER_ABSENCE)
 *   can be added without changing alert evaluation logic structure.
 */
enum class PersistenceAlertType {
    DEVICE_REMAINED_NEARBY
}
