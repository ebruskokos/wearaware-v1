package com.wearaware.app

// PURPOSE: Single-activity host for the Jetpack Compose NavGraph.
// NOTES: All navigation is handled by WearAwareNavGraph via NavController.
// NavGraph is wired in a later task.

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // NavGraph wired in later task
        }
    }
}
