package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate
 *   3 → added fingerprintId, manufacturerIds
 *   4 → added manufacturerDataHex, serviceUuids, txPower, connectable, rawScanBytesHex
 *   5 → added capture_session and captured_device tables
 *   6 → added learning_session and learning_event tables
 * NOTES: fallbackToDestructiveMigration used — all tables are ephemeral/user-clearable.
 */
@Database(
    entities = [
        ScanLogEntity::class,
        CaptureSessionEntity::class,
        CapturedDeviceEntity::class,
        LearningSessionEntity::class,
        LearningEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao
    abstract fun captureDao(): CaptureDao
    abstract fun learningSessionDao(): LearningSessionDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
