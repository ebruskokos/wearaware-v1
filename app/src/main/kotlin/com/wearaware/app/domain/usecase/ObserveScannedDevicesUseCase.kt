package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.repository.BleRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * PURPOSE: Exposes the live stream of observed BLE devices from the BLE repository.
 * NOTES: Returns StateFlow directly — always has a current value (empty list initially).
 */
class ObserveScannedDevicesUseCase @Inject constructor(
    private val bleRepository: BleRepository
) {
    operator fun invoke(): StateFlow<List<ObservedDevice>> = bleRepository.observedDevices
}
