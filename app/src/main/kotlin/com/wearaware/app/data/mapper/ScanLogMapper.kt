package com.wearaware.app.data.mapper

import com.wearaware.app.data.local.ScanLogEntity
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.ScanLogEntry
import com.wearaware.app.domain.model.TargetMatchResult

/**
 * PURPOSE: Bidirectional mapping between ObservedDevice/ScanLogEntry (domain) and
 *   ScanLogEntity (Room). Ensures domain and data layers share no types.
 * NOTES: Enum fields are converted to/from their .name String to keep Room schema
 *   readable and forward-compatible (adding new enum values doesn't break old rows).
 */

/**
 * Maps an ObservedDevice to a ScanLogEntity ready for Room insertion.
 * Timestamp is set to the current time at mapping — not the device's lastSeenAt —
 * because this represents the moment of logging, not the last BLE event.
 */
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
    isTopCandidate = matchResult?.isTopCandidate ?: false
)

/** Maps a Room ScanLogEntity to the domain ScanLogEntry. */
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
    isTopCandidate = isTopCandidate
)
