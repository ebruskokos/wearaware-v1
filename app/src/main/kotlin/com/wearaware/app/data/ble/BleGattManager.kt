package com.wearaware.app.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.wearaware.app.domain.model.GattDiscoveryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface BleGattManager {
    suspend fun connectAndDiscover(deviceAddress: String): GattDiscoveryResult
}

private const val TAG = "WearAware.GattManager"
/** Max number of characteristics to read per session — avoids runaway reads. */
private const val MAX_CHAR_READS = 10

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
                val pendingReads = ArrayDeque<BluetoothGattCharacteristic>()
                val characteristicValues = mutableMapOf<String, String>()

                fun buildResult(g: BluetoothGatt): GattDiscoveryResult {
                    val serviceUuids = g.services.map { it.uuid.toString() }
                    val charUuids = g.services.associate { s ->
                        s.uuid.toString() to s.characteristics.map { it.uuid.toString() }
                    }
                    return GattDiscoveryResult(
                        deviceAddress = deviceAddress,
                        serviceUuids = serviceUuids,
                        characteristicUuids = charUuids,
                        discoveredAt = System.currentTimeMillis(),
                        characteristicValues = characteristicValues.toMap()
                    )
                }

                fun readNext(g: BluetoothGatt) {
                    val next = pendingReads.poll()
                    if (next == null) {
                        // All reads done — disconnect and resolve
                        Log.d(TAG, "Characteristic reads complete: ${characteristicValues.size} values collected")
                        g.disconnect()
                        if (cont.isActive) cont.resume(buildResult(g))
                    } else {
                        val queued = g.readCharacteristic(next)
                        if (!queued) {
                            // Read couldn't be queued — skip and try next
                            Log.d(TAG, "Could not queue read for ${next.uuid} — skipping")
                            readNext(g)
                        }
                    }
                }

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.d(TAG, "GATT connected to $deviceAddress — discovering services")
                                g.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (cont.isActive) cont.resumeWithException(
                                    IOException("GATT disconnected before service discovery (status=$status)")
                                )
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (!cont.isActive) return
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            cont.resumeWithException(IOException("Service discovery failed (status=$status)"))
                            return
                        }
                        Log.d(TAG, "Services discovered: ${g.services.size} services on $deviceAddress")

                        // Queue readable characteristics (up to MAX_CHAR_READS)
                        val readable = g.services
                            .flatMap { it.characteristics }
                            .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }
                            .take(MAX_CHAR_READS)

                        Log.d(TAG, "Readable characteristics found: ${readable.size}")
                        pendingReads.addAll(readable)
                        readNext(g)
                    }

                    @Suppress("DEPRECATION")
                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            handleCharacteristicRead(g, characteristic, characteristic.value, status)
                        }
                    }

                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        handleCharacteristicRead(g, characteristic, value, status)
                    }

                    private fun handleCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray?,
                        status: Int
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.isNotEmpty()) {
                            val hexPrefix = value.toHexString().take(16) // first 8 bytes
                            characteristicValues[characteristic.uuid.toString()] = hexPrefix
                            Log.d(TAG, "Read characteristic ${characteristic.uuid}: $hexPrefix")
                        } else {
                            Log.d(TAG, "Characteristic ${characteristic.uuid} read failed (status=$status)")
                        }
                        if (cont.isActive) readNext(g)
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
