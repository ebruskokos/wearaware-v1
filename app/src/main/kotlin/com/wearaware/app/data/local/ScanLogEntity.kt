package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PURPOSE: Room entity for the local session scan log table.
 *   Mirrors ScanLogEntry domain model but with Room annotations.
 * NOTES: Enum fields stored as String names (not ordinals) for forward compatibility
 *   and readability when querying the database directly.
 */
@Entity(tableName = "scan_log")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    val advertisedName: String?,
    val rawRssi: Int,
    val averagedRssi: Int,
    val proximityLabel: String,
    val visibilityState: String,
    val matchedRuleId: String?,
    val ruleVersion: String?,
    val category: String,
    val confidence: String,
    val evaluationNotes: String?
)
