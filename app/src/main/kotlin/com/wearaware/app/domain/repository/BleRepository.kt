package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.ObservedDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * PURPOSE: Defines the contract for the BLE scanning data source.
 *   Implementations manage scanning lifecycle, RSSI smoothing, device expiry,
 *   and classification — exposing a clean stream of enriched ObservedDevice objects.
 * NOTES: Implemented by BleRepositoryImpl in the data layer.
 *   Domain layer never imports android.bluetooth.
 */
interface BleRepository {
    /**
     * Live stream of all currently tracked BLE devices, sorted by strongest averagedRssi.
     * Emits a new list whenever any device state changes.
     */
    val observedDevices: StateFlow<List<ObservedDevice>>

    /** Returns true if Bluetooth is enabled on the device. */
    val isBleAvailable: Boolean

    /** Starts BLE scanning. Idempotent — safe to call if already scanning. */
    fun startScanning()

    /**
     * Stops BLE scanning and clears the device list.
     * Idempotent — safe to call if not scanning.
     */
    fun stopScanning()
}
