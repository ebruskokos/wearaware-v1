package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.CapturedDeviceEntity
import com.wearaware.app.data.local.CaptureSessionEntity
import com.wearaware.app.domain.model.*

fun CaptureSession.toSessionEntity(): CaptureSessionEntity = CaptureSessionEntity(
    id = id,
    type = type.name,
    startedAt = startedAt,
    stoppedAt = stoppedAt
)

fun CapturedDevice.toDeviceEntity(sessionId: String): CapturedDeviceEntity = CapturedDeviceEntity(
    sessionId = sessionId,
    fingerprintId = fingerprintId,
    advertisedName = advertisedName,
    macAddress = macAddress,
    manufacturerIds = manufacturerIds
        .joinToString(",") { it.toString(16).padStart(4, '0') }
        .ifEmpty { null },
    manufacturerDataSummary = manufacturerDataSummary,
    serviceUuids = serviceUuids.joinToString(",").ifEmpty { null },
    category = category.name,
    companyNames = companyNames.joinToString(",").ifEmpty { null },
    firstSeenInCapture = firstSeenInCapture,
    lastSeenInCapture = lastSeenInCapture,
    peakRssi = peakRssi,
    averageRssi = averageRssi,
    seenCount = seenCount,
    visibleAtStop = visibleAtStop,
    targetMatchScore = targetMatchScore,
    targetMatchSignals = targetMatchSignals.joinToString(";").ifEmpty { null }
)

fun CaptureSessionEntity.toDomain(devices: List<CapturedDeviceEntity>): CaptureSession = CaptureSession(
    id = id,
    type = CaptureType.valueOf(type),
    startedAt = startedAt,
    stoppedAt = stoppedAt,
    devices = devices.map { it.toDomain() }
)

fun CapturedDeviceEntity.toDomain(): CapturedDevice = CapturedDevice(
    fingerprintId = fingerprintId,
    advertisedName = advertisedName,
    macAddress = macAddress,
    manufacturerIds = manufacturerIds
        ?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt(16) }
        ?: emptyList(),
    manufacturerDataSummary = manufacturerDataSummary,
    serviceUuids = serviceUuids
        ?.split(",")?.filter { it.isNotEmpty() }
        ?: emptyList(),
    category = DeviceCategory.valueOf(category),
    companyNames = companyNames
        ?.split(",")?.filter { it.isNotEmpty() }
        ?: emptyList(),
    firstSeenInCapture = firstSeenInCapture,
    lastSeenInCapture = lastSeenInCapture,
    peakRssi = peakRssi,
    averageRssi = averageRssi,
    seenCount = seenCount,
    visibleAtStop = visibleAtStop,
    targetMatchScore = targetMatchScore,
    targetMatchSignals = targetMatchSignals
        ?.split(";")?.filter { it.isNotEmpty() }
        ?: emptyList()
)
