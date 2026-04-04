package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * PURPOSE: Determines whether a device qualifies for a persistence alert.
 *   Encapsulates all alert gating logic in one pure Kotlin function.
 * LIMITATIONS: seenDurationMs is computed from firstSeenAt/lastSeenAt which reset
 *   if a device is removed and re-appears. Brief signal loss (< SIGNAL_LOST_AFTER_MS)
 *   does not break continuity — the device stays in the map.
 * NOTES: Returns null (not an exception) when conditions are not met — caller
 *   should treat null as "no alert this cycle."
 */
class EvaluatePersistenceUseCase @Inject constructor() {

    companion object {
        /** Minimum continuous DETECTED_NOW duration to trigger an alert. */
        const val ALERT_THRESHOLD_MS = 60_000L
        /** How long to suppress re-alerting for the same device + alert type. */
        const val COOLDOWN_MS = 120_000L
    }

    /**
     * Returns a PersistenceAlert if all conditions are met, null otherwise.
     *
     * Conditions:
     * 1. Device is DETECTED_NOW (not SIGNAL_LOST)
     * 2. seenDurationMs >= ALERT_THRESHOLD_MS
     * 3. classification.isWearableCandidate == true
     * 4. classification.confidence is HIGH or MEDIUM (not LOW)
     * 5. proximityLabel is NEARBY, STRONG, or VERY_CLOSE
     * 6. No active cooldown for (deviceId + alertType) key
     *
     * @param device The observed device to evaluate.
     * @param lastAlertedAt Map of (deviceId:alertType) → epoch ms of last alert.
     */
    operator fun invoke(
        device: ObservedDevice,
        lastAlertedAt: Map<String, Long>
    ): PersistenceAlert? {
        if (device.visibilityState != VisibilityState.DETECTED_NOW) return null
        if (device.seenDurationMs < ALERT_THRESHOLD_MS) return null
        if (!device.classification.isWearableCandidate) return null
        if (device.classification.confidence == ConfidenceLevel.LOW) return null
        if (device.proximityLabel == ProximityLabel.WEAK ||
            device.proximityLabel == ProximityLabel.UNKNOWN) return null

        val cooldownKey = "${device.id}:${PersistenceAlertType.DEVICE_REMAINED_NEARBY.name}"
        val lastAlert = lastAlertedAt[cooldownKey]
        if (lastAlert != null && System.currentTimeMillis() - lastAlert < COOLDOWN_MS) return null

        return PersistenceAlert(
            deviceId = device.id,
            deviceDisplayLabel = device.classification.displayLabel,
            durationMs = device.seenDurationMs,
            alertType = PersistenceAlertType.DEVICE_REMAINED_NEARBY,
            triggeredAt = System.currentTimeMillis()
        )
    }
}
