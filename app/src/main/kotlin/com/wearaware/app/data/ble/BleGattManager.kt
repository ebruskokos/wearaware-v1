package com.wearaware.app.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.wearaware.app.domain.model.GattDiscoveryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface BleGattManager {
    suspend fun connectAndDiscover(deviceAddress: String): GattDiscoveryResult
}

@Singleton
class BleGattManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BleGattManager {

    override suspend fun connectAndDiscover(deviceAddress: String): GattDiscoveryResult {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(deviceAddress)
        var gatt: BluetoothGatt? = null

        return try {
            suspendCancellableCoroutine { cont ->
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (cont.isActive) cont.resumeWithException(
                                    IOException("GATT disconnected before service discovery (status=$status)")
                                )
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        g.disconnect()
                        if (!cont.isActive) return
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val serviceUuids = g.services.map { it.uuid.toString() }
                            val charUuids = g.services.associate { s ->
                                s.uuid.toString() to s.characteristics.map { it.uuid.toString() }
                            }
                            cont.resume(
                                GattDiscoveryResult(
                                    deviceAddress = deviceAddress,
                                    serviceUuids = serviceUuids,
                                    characteristicUuids = charUuids,
                                    discoveredAt = System.currentTimeMillis()
                                )
                            )
                        } else {
                            cont.resumeWithException(IOException("Service discovery failed (status=$status)"))
                        }
                    }
                }
                gatt = device.connectGatt(context, false, callback)
                cont.invokeOnCancellation {
                    gatt?.disconnect()
                    gatt?.close()
                }
            }
        } finally {
            gatt?.close()
        }
    }
}
