package com.wearaware.app

// PURPOSE: Hilt application entry point.
// NOTES: All @Singleton Hilt components are scoped to this application lifetime.
//   Version info is logged once at startup for diagnostics.

import android.app.Application
import android.util.Log
import com.wearaware.app.util.AboutInfo
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WearAwareApplication : Application() {

    @Inject
    lateinit var aboutInfo: AboutInfo

    override fun onCreate() {
        super.onCreate()
        Log.i(
            "WearAware",
            "WearAware started — version=${aboutInfo.appVersion} " +
                "rules=${aboutInfo.ruleSetVersion} hash=${aboutInfo.ruleSetHash}"
        )
    }
}
