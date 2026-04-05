package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Builds a KnownTargetSignature from BLE advertising data (ObservedDevice) +
 * GATT service discovery result. The full Pair & Learn path uses both inputs.
 * If observedDevice is null (device not in active scan), only GATT data is used.
 */
class BuildKnownTargetSignatureUseCase @Inject constructor() {

    operator fun invoke(
        observedDevice: ObservedDevice?,
        gattResult: GattDiscoveryResult
    ): KnownTargetSignature {
        val prefixes = extractPrefixesFromFingerprintMap(observedDevice?.fingerprint?.manufacturerDataHex)
        return KnownTargetSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = observedDevice?.id ?: gattResult.deviceAddress,
            manufacturerIds = observedDevice?.fingerprint?.manufacturerIds ?: emptyList(),
            manufacturerDataPrefixes = prefixes,
            serviceUuids = observedDevice?.fingerprint?.serviceUuids ?: emptyList(),
            gattServiceUuids = gattResult.serviceUuids,
            behaviorProfile = observedDevice?.let {
                KnownBehaviorProfile(
                    typicalRssiAtClose = it.averagedRssi,
                    minSeenCount = it.seenCount
                )
            }
        )
    }
}
