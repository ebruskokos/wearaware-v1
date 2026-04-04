package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate (plan superseded)
 *   3 → added fingerprintId, manufacturerIds (BLE intelligence upgrade)
 * NOTES: fallbackToDestructiveMigration used — session logs are ephemeral and user-clearable.
 */
@Database(entities = [ScanLogEntity::class], version = 3, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
