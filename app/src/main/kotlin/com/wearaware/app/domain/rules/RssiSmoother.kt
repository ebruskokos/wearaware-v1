package com.wearaware.app.domain.rules

/**
 * PURPOSE: Smooths noisy BLE RSSI readings using a rolling average over a fixed window.
 *   Reduces UI jitter caused by RSSI variance between consecutive scan results.
 * LIMITATIONS: Rolling average lags behind rapid signal changes. For use cases requiring
 *   faster response, consider an Exponential Moving Average (EMA) — planned for v2.0.
 *   Not thread-safe: each ObservedDevice should have its own RssiSmoother instance.
 * NOTES: Window size is configurable but defaults to ProximityConfig.RSSI_WINDOW_SIZE.
 */
class RssiSmoother(private val windowSize: Int = ProximityConfig.RSSI_WINDOW_SIZE) {

    private val readings = ArrayDeque<Int>(windowSize)

    /**
     * Adds a new RSSI reading to the window and returns the updated rolling average.
     * Drops the oldest reading when the window is full (FIFO).
     *
     * @param rssi Raw RSSI value in dBm (negative integer).
     * @return Rolling average of all readings currently in the window.
     */
    fun addReading(rssi: Int): Int {
        if (readings.size >= windowSize) readings.removeFirst()
        readings.addLast(rssi)
        return readings.average().toInt()
    }

    /**
     * Clears all stored readings. Call when a device re-appears after SIGNAL_LOST
     * to avoid averaging stale readings with fresh ones.
     */
    fun reset() = readings.clear()

    /**
     * Returns the current rolling average without adding a new reading.
     * Returns null if no readings have been added yet.
     */
    fun currentAverage(): Int? =
        if (readings.isEmpty()) null else readings.average().toInt()
}
