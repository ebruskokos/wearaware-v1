package com.wearaware.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val evaluationNotes: String?,
    @ColumnInfo(defaultValue = "NULL") val fingerprintId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val manufacturerIds: String? = null,
    @ColumnInfo(defaultValue = "NULL") val targetMatchScore: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val targetMatchReason: String? = null,
    @ColumnInfo(defaultValue = "0") val isTopCandidate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val manufacturerDataHex: String? = null,
    @ColumnInfo(defaultValue = "NULL") val serviceUuids: String? = null,
    @ColumnInfo(defaultValue = "NULL") val txPower: Int? = null,
    @ColumnInfo(defaultValue = "0") val connectable: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val rawScanBytesHex: String? = null
)
