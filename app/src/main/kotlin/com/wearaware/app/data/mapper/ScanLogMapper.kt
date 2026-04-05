package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult

fun ObservedDevice.toScanLogEntity(matchResult: TargetMatchResult? = null): ScanLogEntity = ScanLogEntity(
    timestamp = System.currentTimeMillis(),
    deviceId = id,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel.name,
    visibilityState = visibilityState.name,
    matchedRuleId = classification.matchedRuleId,
    ruleVersion = classification.ruleVersion,
    category = classification.category.name,
    confidence = classification.confidence.name,
    evaluationNotes = classification.evaluationNotes,
    fingerprintId = fingerprint?.fingerprintId,
    manufacturerIds = fingerprint?.manufacturerIds
        ?.joinToString(",") { it.toString(16).padStart(4, '0') },
    targetMatchScore = matchResult?.score,
    targetMatchReason = matchResult?.matchedSignals?.joinToString("; "),
    isTopCandidate = matchResult?.isTopCandidate ?: false,
    manufacturerDataHex = fingerprint?.manufacturerDataHex
        ?.entries?.joinToString(",") { (id, hex) ->
            "${id.toString(16).padStart(4, '0')}:$hex"
        },
    serviceUuids = fingerprint?.serviceUuids?.joinToString(",")?.takeIf { it.isNotEmpty() },
    txPower = fingerprint?.txPower,
    connectable = rawBleData?.isConnectable ?: false,
    // Store first 32 bytes (64 hex chars) to keep log rows compact
    rawScanBytesHex = rawBleData?.rawScanBytesHex?.take(64)
)

fun ScanLogEntity.toDomain(): ScanLogEntry = ScanLogEntry(
    id = id,
    timestamp = timestamp,
    deviceId = deviceId,
    advertisedName = advertisedName,
    rawRssi = rawRssi,
    averagedRssi = averagedRssi,
    proximityLabel = proximityLabel,
    visibilityState = visibilityState,
    matchedRuleId = matchedRuleId,
    ruleVersion = ruleVersion,
    category = category,
    confidence = confidence,
    evaluationNotes = evaluationNotes,
    fingerprintId = fingerprintId,
    manufacturerIds = manufacturerIds,
    targetMatchScore = targetMatchScore,
    targetMatchReason = targetMatchReason,
    isTopCandidate = isTopCandidate,
    manufacturerDataHex = manufacturerDataHex,
    serviceUuids = serviceUuids,
    txPower = txPower,
    connectable = connectable,
    rawScanBytesHex = rawScanBytesHex
)
