package com.wearaware.app

// PURPOSE: Hilt application entry point.
// NOTES: All @Singleton Hilt components are scoped to this application lifetime.

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearAwareApplication : Application()
