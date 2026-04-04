package com.wearaware.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.wearaware.app.ui.navigation.WearAwareNavGraph
import com.wearaware.app.ui.theme.WearAwareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAwareTheme {
                val navController = rememberNavController()
                WearAwareNavGraph(navController = navController)
            }
        }
    }
}
