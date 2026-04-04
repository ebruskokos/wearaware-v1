package com.wearaware.app.ui.theme

import androidx.compose.ui.graphics.Color

// Signal bar colors — no green (see product-decisions.md Decision 3)
val SignalVeryClose = Color(0xFFE53935)   // red — very close
val SignalStrong    = Color(0xFFFF6F00)   // orange — strong
val SignalNearby    = Color(0xFFFFB300)   // yellow — nearby
val SignalWeak      = Color(0xFF9E9E9E)   // grey — weak
val SignalUnknown   = Color(0xFF9E9E9E)   // grey — unknown

// Visibility state dots
val DotDetected   = Color(0xFF4CAF50)  // green dot — actively detected
val DotSignalLost = Color(0xFF9E9E9E)  // grey dot — signal lost
