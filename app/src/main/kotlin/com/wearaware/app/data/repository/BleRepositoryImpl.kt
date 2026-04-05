package com.wearaware.app.data.repository

import com.wearaware.app.data.ble.BleScanner
import com.wearaware.app.data.ble.toFingerprint
import com.wearaware.app.data.ble.toHexString
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.rules.FingerprintClassifier
import com.wearaware.app.domain.rules.ProximityConfig
import com.wearaware.app.domain.rules.RssiSmoother
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val classifier: FingerprintClassifier
) : BleRepository {

    private data class DeviceState(
        val smoother: RssiSmoother = RssiSmoother(),
        val macAddress: String,
        val fingerprint: DeviceFingerprint,
        val rawBleData: BleDebugData,
        val firstSeenAt: Long,
        var lastSeenAt: Long,
        var seenCount: Int = 1,
        var rawRssi: Int,
        var averagedRssi: Int,
        val advertisedName: String?,
        val manufacturerData: Map<Int, ByteArray>,
        val serviceUuids: List<String>,
        val txPowerLevel: Int?
    )

    private val deviceStates = ConcurrentHashMap<String, DeviceState>()
    private val _observedDevices = MutableStateFlow<List<ObservedDevice>>(emptyList())
    override val observedDevices: StateFlow<List<ObservedDevice>> = _observedDevices.asStateFlow()
    override val isBleAvailable: Boolean get() = bleScanner.isBleAvailable
    override val isScanning: Boolean get() = bleScanner.isScanning

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var expiryJob: Job? = null

    companion object {
        /** Hard cap on tracked devices — drops weakest RSSI to prevent unbounded growth. */
        private const val MAX_TRACKED_DEVICES = 50
    }

    override fun startScanning() {
        if (bleScanner.isScanning) return
        bleScanner.startScanning()

        scanJob = repositoryScope.launch {
            bleScanner.results.collect { raw ->
                processRawScanResult(raw)
                emitDeviceList()
            }
        }

        expiryJob = repositoryScope.launch {
            while (isActive) {
                delay(2_000L)
                removeExpiredDevices()
                emitDeviceList()
            }
        }
    }

    override fun stopScanning() {
        bleScanner.stopScanning()
        scanJob?.cancel()
        expiryJob?.cancel()
        scanJob = null
        expiryJob = null
        deviceStates.clear()
        _observedDevices.value = emptyList()
    }

    override fun setAdaptiveScanMode(locked: Boolean) {
        bleScanner.setScanMode(balanced = locked)
    }

    private fun processRawScanResult(raw: RawScanResult) {
        val fp = raw.toFingerprint()
        val key = fp.fingerprintId
        val now = System.currentTimeMillis()
        val existing = deviceStates[key]

        if (existing != null) {
            existing.lastSeenAt = now
            existing.seenCount++
            existing.rawRssi = raw.rssi
            existing.averagedRssi = existing.smoother.addReading(raw.rssi)
        } else {
            // Enforce device cap before inserting — drop weakest RSSI device if over limit
            if (deviceStates.size >= MAX_TRACKED_DEVICES) {
                val weakestKey = deviceStates.minByOrNull { it.value.averagedRssi }?.key
                if (weakestKey != null) deviceStates.remove(weakestKey)
            }
            val smoother = RssiSmoother()
            val debugData = BleDebugData(
                serviceSolicitationUuids = raw.serviceSolicitationUuids,
                serviceDataHex = raw.serviceData.mapValues { (_, v) -> v.toHexString() },
                rawScanBytesHex = raw.rawScanBytes?.toHexString(),
                isConnectable = raw.isConnectable,
                advertisingFlags = raw.advertisingFlags,
                timestampNanos = raw.timestampNanos,
                primaryPhy = raw.primaryPhy,
                secondaryPhy = raw.secondaryPhy,
                advertisingSid = raw.advertisingSid,
                periodicAdvertisingInterval = raw.periodicAdvertisingInterval,
                deviceType = raw.deviceType,
                bondState = raw.bondState
            )
            deviceStates[key] = DeviceState(
                smoother = smoother,
                macAddress = raw.address,
                fingerprint = fp,
                rawBleData = debugData,
                firstSeenAt = now,
                lastSeenAt = now,
                rawRssi = raw.rssi,
                averagedRssi = smoother.addReading(raw.rssi),
                advertisedName = raw.advertisedName,
                manufacturerData = raw.manufacturerData,
                serviceUuids = raw.serviceUuids,
                txPowerLevel = raw.txPowerLevel
            )
        }
    }

    private fun removeExpiredDevices() {
        val now = System.currentTimeMillis()
        deviceStates.entries.removeIf { (_, state) ->
            now - state.lastSeenAt > ProximityConfig.REMOVE_AFTER_MS
        }
    }

    private fun emitDeviceList() {
        val now = System.currentTimeMillis()
        val devices = deviceStates.entries
            .map { (fingerprintId, state) ->
                val visibilityState =
                    if (now - state.lastSeenAt > ProximityConfig.SIGNAL_LOST_AFTER_MS)
                        VisibilityState.SIGNAL_LOST
                    else
                        VisibilityState.DETECTED_NOW

                val rawScanForClassification = RawScanResult(
                    address = state.macAddress,
                    advertisedName = state.advertisedName,
                    bluetoothDeviceName = null,
                    rssi = state.rawRssi,
                    manufacturerData = state.manufacturerData,
                    serviceUuids = state.serviceUuids,
                    serviceData = emptyMap(),
                    txPowerLevel = state.txPowerLevel,
                    advertisingFlags = null,
                    isConnectable = false,
                    deviceType = 0,
                    bondState = 10,
                    timestampMs = state.lastSeenAt
                )
                val classification = classifier.classify(rawScanForClassification)

                ObservedDevice(
                    id = fingerprintId,
                    advertisedName = state.advertisedName,
                    rawRssi = state.rawRssi,
                    averagedRssi = state.averagedRssi,
                    proximityLabel = ProximityConfig.labelFromRssi(state.averagedRssi),
                    visibilityState = visibilityState,
                    firstSeenAt = state.firstSeenAt,
                    lastSeenAt = state.lastSeenAt,
                    seenCount = state.seenCount,
                    classification = classification,
                    persistenceAlert = null,
                    macAddress = state.macAddress,
                    fingerprint = state.fingerprint,
                    companyNames = state.fingerprint.manufacturerNames,
                    rawBleData = state.rawBleData
                )
            }
            .sortedByDescending { it.averagedRssi }

        _observedDevices.value = devices
    }
}
