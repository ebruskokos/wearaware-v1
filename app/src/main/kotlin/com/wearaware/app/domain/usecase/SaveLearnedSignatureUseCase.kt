package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.CapturedDevice
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject

class SaveLearnedSignatureUseCase @Inject constructor(
    private val repository: LearnedSignatureRepository
) {
    operator fun invoke(device: CapturedDevice): LearnedDeviceSignature {
        val prefixes = extractPrefixesFromSummary(device.manufacturerDataSummary)
        val signature = LearnedDeviceSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = device.fingerprintId,
            manufacturerIds = device.manufacturerIds,
            manufacturerDataPrefixes = prefixes,
            serviceUuids = device.serviceUuids
        )
        repository.save(signature)
        return signature
    }
}
