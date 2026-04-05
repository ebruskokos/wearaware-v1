package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CaptureType

interface CaptureRepository {
    suspend fun saveSession(session: CaptureSession)
    suspend fun getSession(type: CaptureType): CaptureSession?
    suspend fun deleteSession(type: CaptureType)
}
