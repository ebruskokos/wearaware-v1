package com.wearaware.app.data.local

import androidx.room.*

@Dao
interface CaptureDao {

    @Query("SELECT * FROM capture_session WHERE type = :type LIMIT 1")
    suspend fun getSession(type: String): CaptureSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CaptureSessionEntity)

    @Query("SELECT * FROM captured_device WHERE session_id = :sessionId")
    suspend fun getDevicesForSession(sessionId: String): List<CapturedDeviceEntity>

    @Insert
    suspend fun insertDevices(devices: List<CapturedDeviceEntity>)

    @Query("DELETE FROM captured_device WHERE session_id = :sessionId")
    suspend fun deleteDevicesForSession(sessionId: String)

    @Query("DELETE FROM capture_session WHERE type = :type")
    suspend fun deleteSession(type: String)
}
