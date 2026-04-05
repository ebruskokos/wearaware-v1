package com.wearaware.app.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.wearaware.app.domain.model.RawScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: The ONLY class in WearAware that imports android.bluetooth.
 *   Wraps BluetoothLeScanner and exposes scan results as a Flow<RawScanResult>.
 * LIMITATIONS:
 *   - Scanning stops when the app is backgrounded (v1.0 — no foreground service).
 *   - Requires BLUETOOTH_SCAN (API 31+) or ACCESS_FINE_LOCATION (API 23–30).
 *   - If Bluetooth is disabled, startScanning() is a no-op; check isBleAvailable first.
 *   - ScanFailed errors are logged but not surfaced to callers in v1.0.
 * NOTES: results is a SharedFlow with a buffer of 64 to handle rapid scan bursts.
 *   DROP_OLDEST prevents backpressure at the cost of dropping older readings under load.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _results = MutableSharedFlow<RawScanResult>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Continuous stream of BLE scan results. Emits whenever a device is discovered. */
    val results: Flow<RawScanResult> = _results.asSharedFlow()

    private var scanCallback: ScanCallback? = null
    private var currentScanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY

    /** True if BLE scanning is currently active. */
    var isScanning: Boolean = false
        private set

    /** True if Bluetooth is supported and enabled on this device. */
    val isBleAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Starts BLE scanning using [currentScanMode].
     * Idempotent — does nothing if already scanning or if BLE is unavailable.
     */
    fun startScanning() {
        if (isScanning || !isBleAvailable) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(currentScanMode)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                _results.tryEmit(result.toRawScanResult())
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { _results.tryEmit(it.toRawScanResult()) }
            }
            override fun onScanFailed(errorCode: Int) {
                // ScanFailed is documented — not crash-worthy. Future: expose as Flow event.
            }
        }

        scanner.startScan(null, settings, scanCallback!!)
        isScanning = true
    }

    /**
     * Stops BLE scanning and clears the active callback.
     * Idempotent — safe to call if not scanning.
     */
    fun stopScanning() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.let { scanner ->
            scanCallback?.let { scanner.stopScan(it) }
        }
        scanCallback = null
        isScanning = false
    }

    /**
     * Switches the BLE scan mode.
     * [balanced] = true uses SCAN_MODE_BALANCED (target locked — save battery).
     * [balanced] = false uses SCAN_MODE_LOW_LATENCY (searching — fast discovery).
     * Restarts the scanner if the mode actually changes and scanning is active.
     */
    fun setScanMode(balanced: Boolean) {
        val newMode = if (balanced) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_LATENCY
        if (newMode == currentScanMode) return
        currentScanMode = newMode
        if (isScanning) {
            stopScanning()
            startScanning()
        }
    }
}
