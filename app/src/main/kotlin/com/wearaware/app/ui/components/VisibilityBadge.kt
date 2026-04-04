package com.wearaware.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.VisibilityState
import com.wearaware.app.ui.theme.DotDetected
import com.wearaware.app.ui.theme.DotSignalLost

@Composable
fun VisibilityBadge(
    state: VisibilityState,
    modifier: Modifier = Modifier
) {
    val (color, description) = when (state) {
        VisibilityState.DETECTED_NOW -> DotDetected   to "Active: device detected now"
        VisibilityState.SIGNAL_LOST  -> DotSignalLost to "Signal lost: device not currently detected"
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .semantics { contentDescription = description }
            .background(color = color, shape = CircleShape)
    )
}
