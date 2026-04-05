package com.wearaware.app.data.repository

import com.wearaware.app.data.local.CaptureDao
import com.wearaware.app.data.mapper.toDomain
import com.wearaware.app.data.mapper.toDeviceEntity
import com.wearaware.app.data.mapper.toSessionEntity
import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CaptureType
import com.wearaware.app.domain.repository.CaptureRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepositoryImpl @Inject constructor(
    private val captureDao: CaptureDao
) : CaptureRepository {

    override suspend fun saveSession(session: CaptureSession) {
        captureDao.insertSession(session.toSessionEntity())
        captureDao.insertDevices(session.devices.map { it.toDeviceEntity(session.id) })
    }

    override suspend fun getSession(type: CaptureType): CaptureSession? {
        val sessionEntity = captureDao.getSession(type.name) ?: return null
        val deviceEntities = captureDao.getDevicesForSession(sessionEntity.id)
        return sessionEntity.toDomain(deviceEntities)
    }

    override suspend fun deleteSession(type: CaptureType) {
        val existing = captureDao.getSession(type.name)
        if (existing != null) {
            captureDao.deleteDevicesForSession(existing.id)
        }
        captureDao.deleteSession(type.name)
    }
}
