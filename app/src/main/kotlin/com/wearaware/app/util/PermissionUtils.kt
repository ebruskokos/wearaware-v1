package com.wearaware.app.util

import android.Manifest
import android.os.Build

object PermissionUtils {
    val BLE_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun allGranted(grantResults: Map<String, Boolean>): Boolean =
        grantResults.values.all { it }
}
