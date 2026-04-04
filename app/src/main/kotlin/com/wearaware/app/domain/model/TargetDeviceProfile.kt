package com.wearaware.app.domain.model

data class TargetDeviceProfile(
    val friendlyName: String,
    val modelHint: String,
    val brandHint: String,
    val category: DeviceCategory
)
