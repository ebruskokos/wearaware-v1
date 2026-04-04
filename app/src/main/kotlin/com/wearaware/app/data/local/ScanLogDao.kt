package com.wearaware.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * PURPOSE: Data Access Object for the scan_log table.
 * NOTES: observeAll() uses Flow — Room emits a new list whenever the table changes.
 *   deleteAll() uses a suspend function — call from a coroutine, never from the main thread.
 */
@Dao
interface ScanLogDao {
    /** Inserts a new scan log entry. Auto-generates the id. */
    @Insert
    suspend fun insert(entry: ScanLogEntity)

    /** Returns all entries ordered newest first, as a live Flow. */
    @Query("SELECT * FROM scan_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScanLogEntity>>

    /** Deletes all session log entries. Irreversible. */
    @Query("DELETE FROM scan_log")
    suspend fun deleteAll()
}
