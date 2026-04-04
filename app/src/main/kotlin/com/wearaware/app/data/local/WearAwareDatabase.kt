package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * PURPOSE: Room database definition for WearAware local storage.
 *   Currently contains one table: scan_log.
 * LIMITATIONS: exportSchema = false means schema is not exported to a file.
 *   Set to true and configure schemaLocation if schema history tracking is needed.
 * NOTES: Version is 1 for initial release. Increment + add Migration when schema changes.
 */
@Database(entities = [ScanLogEntity::class], version = 1, exportSchema = false)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
