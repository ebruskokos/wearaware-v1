package com.wearaware.app.domain.model

/**
 * PURPOSE: Determines which devices are shown on ScanScreen.
 *   Each value implements matches() — applied as a predicate on ObservedDevice.
 * NOTES: Filtering is a computed property on ScanUiState; the ViewModel only stores the
 *   active filter enum value. Apple devices remain visible under ALL and are never promoted
 *   as target candidates for Meta glasses; HIDE_APPLE lets the user reduce visual noise.
 */
enum class ScanFilter(val label: String) {
    ALL("All"),
    GLASSES_CANDIDATES("Glasses"),
    META_DEVICES("Meta"),
    HIDE_APPLE("Hide Apple"),
    UNKNOWN_ONLY("Unknown"),
    STRONG_SIGNAL("Strong Signal");

    fun matches(device: ObservedDevice): Boolean = when (this) {
        ALL -> true
        GLASSES_CANDIDATES ->
            device.classification.category == DeviceCategory.SMART_GLASSES ||
                device.classification.category == DeviceCategory.CAMERA_CAPABLE_WEARABLE
        META_DEVICES ->
            device.companyNames.any { it.contains("Meta", ignoreCase = true) }
        HIDE_APPLE ->
            device.companyNames.none { it.contains("Apple", ignoreCase = true) }
        UNKNOWN_ONLY ->
            device.classification.category == DeviceCategory.UNKNOWN_BLE_DEVICE
        STRONG_SIGNAL ->
            device.proximityLabel == ProximityLabel.VERY_CLOSE ||
                device.proximityLabel == ProximityLabel.STRONG ||
                device.proximityLabel == ProximityLabel.NEARBY
    }
}
