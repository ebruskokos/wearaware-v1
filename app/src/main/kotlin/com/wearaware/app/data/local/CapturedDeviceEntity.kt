package com.wearaware.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_device")
data class CapturedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val fingerprintId: String,
    val advertisedName: String?,
    val macAddress: String?,
    /** Comma-sep hex e.g. "0075,004c". Canonical for manufacturer logic. */
    val manufacturerIds: String?,
    /** "0075:deadbeef,..." — display only. */
    val manufacturerDataSummary: String?,
    /** Comma-sep UUID strings. */
    val serviceUuids: String?,
    val category: String,
    /** Comma-sep — display only. */
    val companyNames: String?,
    val firstSeenInCapture: Long,
    val lastSeenInCapture: Long,
    val peakRssi: Int,
    val averageRssi: Int,
    val seenCount: Int,
    @ColumnInfo(defaultValue = "0") val visibleAtStop: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val targetMatchScore: Int? = null,
    /** Semicolon-sep signal strings. */
    @ColumnInfo(defaultValue = "NULL") val targetMatchSignals: String? = null
)
